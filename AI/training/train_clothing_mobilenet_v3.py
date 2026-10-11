from __future__ import annotations

import argparse
import copy
import random
from pathlib import Path

import numpy as np
import torch
from PIL import ImageFile
from torch import nn
from torch.utils.data import DataLoader
from torchvision import datasets, transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small
from tqdm import tqdm


CLOTHING_CATEGORIES = [
    "T-shirt",
    "Shirt",
    "Hoodie",
    "Sweater",
    "Pants",
    "Skirt",
    "Dress",
    "Outerwear",
]
ARCHITECTURE = "mobilenet_v3_small"
INPUT_SIZE = 224
MEAN = [0.485, 0.456, 0.406]
STD = [0.229, 0.224, 0.225]

ImageFile.LOAD_TRUNCATED_IMAGES = True


class FixedClassImageFolder(datasets.ImageFolder):
    """ImageFolder with a stable class order shared by training and inference."""

    def find_classes(self, directory: str) -> tuple[list[str], dict[str, int]]:
        root = Path(directory)
        missing = [category for category in CLOTHING_CATEGORIES if not (root / category).is_dir()]
        if missing:
            raise FileNotFoundError(
                f"{directory} is missing class folders: {', '.join(missing)}"
            )
        return CLOTHING_CATEGORIES, {
            category: index for index, category in enumerate(CLOTHING_CATEGORIES)
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train an 8-class clothing classifier with MobileNetV3-Small."
    )
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("AI/models/clothing_mobilenet_v3_small.pth"),
    )
    parser.add_argument("--epochs-head", type=int, default=5)
    parser.add_argument("--epochs-fine", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lr-head", type=float, default=1e-3)
    parser.add_argument("--lr-fine", type=float, default=1e-4)
    parser.add_argument("--num-workers", type=int, default=2)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.benchmark = True


def build_transforms() -> tuple[transforms.Compose, transforms.Compose]:
    train_transform = transforms.Compose(
        [
            transforms.RandomResizedCrop(INPUT_SIZE, scale=(0.75, 1.0)),
            transforms.RandomHorizontalFlip(),
            transforms.ColorJitter(brightness=0.15, contrast=0.15, saturation=0.15),
            transforms.ToTensor(),
            transforms.Normalize(mean=MEAN, std=STD),
        ]
    )
    eval_transform = transforms.Compose(
        [
            transforms.Resize(256),
            transforms.CenterCrop(INPUT_SIZE),
            transforms.ToTensor(),
            transforms.Normalize(mean=MEAN, std=STD),
        ]
    )
    return train_transform, eval_transform


def build_dataloaders(
    data_dir: Path,
    batch_size: int,
    num_workers: int,
) -> tuple[dict[str, DataLoader], dict[str, FixedClassImageFolder]]:
    train_transform, eval_transform = build_transforms()
    split_transforms = {
        "train": train_transform,
        "val": eval_transform,
        "test": eval_transform,
    }

    datasets_by_split: dict[str, FixedClassImageFolder] = {}
    loaders: dict[str, DataLoader] = {}

    for split in ("train", "val", "test"):
        split_dir = data_dir / split
        if split in ("train", "val") and not split_dir.is_dir():
            raise FileNotFoundError(f"Required dataset split not found: {split_dir}")
        if split == "test" and not split_dir.is_dir():
            continue

        dataset = FixedClassImageFolder(split_dir, transform=split_transforms[split])
        datasets_by_split[split] = dataset
        loaders[split] = DataLoader(
            dataset,
            batch_size=batch_size,
            shuffle=(split == "train"),
            num_workers=num_workers,
            pin_memory=torch.cuda.is_available(),
        )
        print(f"[Data] {split}: {len(dataset)} images")

    return loaders, datasets_by_split


def build_model(device: torch.device) -> nn.Module:
    model = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)
    input_features = model.classifier[-1].in_features
    model.classifier[-1] = nn.Linear(input_features, len(CLOTHING_CATEGORIES))
    return model.to(device)


def set_feature_training(model: nn.Module, train_features: bool) -> None:
    for parameter in model.parameters():
        parameter.requires_grad = train_features
    for parameter in model.classifier.parameters():
        parameter.requires_grad = True


def run_epoch(
    model: nn.Module,
    loader: DataLoader,
    criterion: nn.Module,
    device: torch.device,
    optimizer: torch.optim.Optimizer | None = None,
) -> tuple[float, float]:
    is_train = optimizer is not None
    model.train(is_train)

    total_loss = 0.0
    correct = 0
    total = 0

    progress = tqdm(loader, leave=False, desc="train" if is_train else "eval")
    for images, targets in progress:
        images = images.to(device, non_blocking=True)
        targets = targets.to(device, non_blocking=True)

        with torch.set_grad_enabled(is_train):
            outputs = model(images)
            loss = criterion(outputs, targets)

            if is_train:
                optimizer.zero_grad(set_to_none=True)
                loss.backward()
                optimizer.step()

        batch_size = targets.size(0)
        total_loss += loss.item() * batch_size
        predictions = outputs.argmax(dim=1)
        correct += (predictions == targets).sum().item()
        total += batch_size
        progress.set_postfix(loss=total_loss / total, acc=correct / total)

    return total_loss / total, correct / total


def evaluate_with_confusion_matrix(
    model: nn.Module,
    loader: DataLoader,
    device: torch.device,
) -> tuple[float, list[list[int]]]:
    model.eval()
    matrix = torch.zeros(
        (len(CLOTHING_CATEGORIES), len(CLOTHING_CATEGORIES)),
        dtype=torch.int64,
    )
    correct = 0
    total = 0

    with torch.inference_mode():
        for images, targets in tqdm(loader, leave=False, desc="test"):
            images = images.to(device, non_blocking=True)
            targets = targets.to(device, non_blocking=True)
            outputs = model(images)
            predictions = outputs.argmax(dim=1)

            correct += (predictions == targets).sum().item()
            total += targets.size(0)
            for target, prediction in zip(targets.cpu(), predictions.cpu()):
                matrix[int(target), int(prediction)] += 1

    return correct / total, matrix.tolist()


def save_checkpoint(
    output: Path,
    model_state_dict: dict[str, torch.Tensor],
    val_acc: float,
    test_acc: float | None,
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "model_state_dict": model_state_dict,
            "categories": CLOTHING_CATEGORIES,
            "val_acc": val_acc,
            "test_acc": test_acc,
            "architecture": ARCHITECTURE,
            "input_size": INPUT_SIZE,
        },
        output,
    )
    print(f"[Checkpoint] Saved best model to {output}")


def train_phase(
    phase_name: str,
    model: nn.Module,
    loaders: dict[str, DataLoader],
    criterion: nn.Module,
    optimizer: torch.optim.Optimizer,
    device: torch.device,
    epochs: int,
    best_val_acc: float,
    best_state_dict: dict[str, torch.Tensor] | None,
) -> tuple[float, dict[str, torch.Tensor]]:
    for epoch in range(1, epochs + 1):
        train_loss, train_acc = run_epoch(
            model, loaders["train"], criterion, device, optimizer
        )
        val_loss, val_acc = run_epoch(model, loaders["val"], criterion, device)
        print(
            f"[{phase_name}] epoch {epoch}/{epochs} "
            f"train_loss={train_loss:.4f} train_acc={train_acc:.4f} "
            f"val_loss={val_loss:.4f} val_acc={val_acc:.4f}"
        )

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            best_state_dict = copy.deepcopy(model.state_dict())
            print(f"[{phase_name}] New best val_acc={best_val_acc:.4f}")

    if best_state_dict is None:
        raise RuntimeError("training finished without producing a checkpoint state")

    return best_val_acc, best_state_dict


def print_confusion_matrix(matrix: list[list[int]]) -> None:
    print("[Test] Confusion matrix rows=true labels, columns=predicted labels")
    print("labels: " + ", ".join(CLOTHING_CATEGORIES))
    for category, row in zip(CLOTHING_CATEGORIES, matrix):
        print(f"{category}: {row}")


def main() -> None:
    args = parse_args()
    set_seed(args.seed)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[Device] Using {device}")

    loaders, _ = build_dataloaders(args.data_dir, args.batch_size, args.num_workers)
    model = build_model(device)
    criterion = nn.CrossEntropyLoss()

    best_val_acc = -1.0
    best_state_dict: dict[str, torch.Tensor] | None = None

    if args.epochs_head > 0:
        print("[Phase 1] Training classifier head with frozen features")
        set_feature_training(model, train_features=False)
        optimizer = torch.optim.AdamW(
            (parameter for parameter in model.parameters() if parameter.requires_grad),
            lr=args.lr_head,
        )
        best_val_acc, best_state_dict = train_phase(
            "head",
            model,
            loaders,
            criterion,
            optimizer,
            device,
            args.epochs_head,
            best_val_acc,
            best_state_dict,
        )

    if args.epochs_fine > 0:
        print("[Phase 2] Fine-tuning all layers")
        set_feature_training(model, train_features=True)
        optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr_fine)
        best_val_acc, best_state_dict = train_phase(
            "fine",
            model,
            loaders,
            criterion,
            optimizer,
            device,
            args.epochs_fine,
            best_val_acc,
            best_state_dict,
        )

    if best_state_dict is None:
        raise ValueError("At least one of --epochs-head or --epochs-fine must be > 0.")

    model.load_state_dict(best_state_dict)
    test_acc = None
    if "test" in loaders:
        test_acc, matrix = evaluate_with_confusion_matrix(model, loaders["test"], device)
        print(f"[Test] accuracy={test_acc:.4f}")
        print_confusion_matrix(matrix)
    else:
        print("[Test] No test split found; skipping test accuracy and confusion matrix.")

    save_checkpoint(args.output, best_state_dict, best_val_acc, test_acc)
    print(f"[Done] best_val_acc={best_val_acc:.4f}")


if __name__ == "__main__":
    main()

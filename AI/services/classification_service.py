from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import os

import torch
from PIL import Image, UnidentifiedImageError
from torchvision import transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small


@dataclass(frozen=True)
class ClassificationResult:
    category: str
    confidence: float


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
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
WEIGHTS = MobileNet_V3_Small_Weights.DEFAULT
DEFAULT_MODEL_PATH = (
    Path(__file__).resolve().parents[1] / "models" / "clothing_mobilenet_v3_small.pth"
)
MODEL_PATH = Path(os.getenv("CLOTHING_MODEL_PATH", DEFAULT_MODEL_PATH))
ALLOW_UNTRAINED_CLASSIFIER = os.getenv("ALLOW_UNTRAINED_CLASSIFIER") == "1"
PREPROCESS = transforms.Compose(
    [
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ]
)
MODEL = None


def _torch_load_checkpoint(path: Path) -> object:
    try:
        return torch.load(path, map_location=DEVICE, weights_only=True)
    except TypeError:
        return torch.load(path, map_location=DEVICE)
    except Exception as error:
        try:
            return torch.load(path, map_location=DEVICE)
        except Exception as fallback_error:
            raise RuntimeError(
                f"failed to load clothing checkpoint at {path}: {fallback_error}"
            ) from error


def _build_model() -> torch.nn.Module:
    model = mobilenet_v3_small(weights=WEIGHTS)
    input_features = model.classifier[-1].in_features
    model.classifier[-1] = torch.nn.Linear(input_features, len(CLOTHING_CATEGORIES))

    if MODEL_PATH.exists():
        try:
            checkpoint = _torch_load_checkpoint(MODEL_PATH)
            if not isinstance(checkpoint, dict) or "model_state_dict" not in checkpoint:
                raise RuntimeError(
                    "checkpoint must be a dict containing 'model_state_dict' and "
                    "'categories'"
                )

            checkpoint_categories = checkpoint.get("categories")
            if checkpoint_categories != CLOTHING_CATEGORIES:
                raise RuntimeError(
                    "checkpoint categories do not match service CLOTHING_CATEGORIES. "
                    f"checkpoint={checkpoint_categories}, service={CLOTHING_CATEGORIES}"
                )

            state_dict = checkpoint["model_state_dict"]
            model.load_state_dict(state_dict)
            print(f"[Classification] Loaded clothing weights from {MODEL_PATH}")
        except Exception as error:
            if not ALLOW_UNTRAINED_CLASSIFIER:
                raise RuntimeError(f"Clothing weight load failed: {error}") from error
            print(f"[Classification] Clothing weight load failed: {error}")
            print(
                "[Classification] ALLOW_UNTRAINED_CLASSIFIER=1 is set; "
                "using pretrained MobileNetV3 features with an untrained classifier head."
            )
    else:
        if not ALLOW_UNTRAINED_CLASSIFIER:
            raise RuntimeError(
                f"No clothing checkpoint found at {MODEL_PATH}. "
                "Train the classifier or set CLOTHING_MODEL_PATH. "
                "For local development only, set ALLOW_UNTRAINED_CLASSIFIER=1."
            )
        print(f"[Classification] No clothing weight file found at {MODEL_PATH}")
        print(
            "[Classification] ALLOW_UNTRAINED_CLASSIFIER=1 is set; "
            "using pretrained MobileNetV3 features with an untrained classifier head."
        )

    model.to(DEVICE)
    model.eval()
    return model


def _get_model() -> torch.nn.Module:
    global MODEL

    if MODEL is None:
        MODEL = _build_model()

    return MODEL


def load_model() -> torch.nn.Module:
    """Load MobileNetV3 once during FastAPI startup and reuse it per request."""
    return _get_model()


def predict_category(image_bytes: bytes) -> ClassificationResult:
    """Predict an 8-class clothing category from image bytes using MobileNetV3.

    Production inference requires a trained clothing checkpoint. Development
    fallback with an untrained classifier is only allowed when
    ALLOW_UNTRAINED_CLASSIFIER=1 is set.
    """
    try:
        image = Image.open(BytesIO(image_bytes)).convert("RGB")
    except (UnidentifiedImageError, OSError) as error:
        raise ValueError("Invalid image bytes: image could not be opened or decoded.") from error

    tensor = PREPROCESS(image).unsqueeze(0).to(DEVICE)

    with torch.inference_mode():
        logits = _get_model()(tensor)
        probabilities = torch.softmax(logits, dim=1)
        confidence, class_index = probabilities.max(dim=1)

    index = int(class_index.item())
    category = CLOTHING_CATEGORIES[index]

    return ClassificationResult(
        category=category,
        confidence=round(float(confidence.item()), 4),
    )

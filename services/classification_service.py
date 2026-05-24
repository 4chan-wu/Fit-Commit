from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import os

import torch
from PIL import Image
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
PREPROCESS = transforms.Compose(
    [
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ]
)
MODEL = None


def _build_model() -> torch.nn.Module:
    model = mobilenet_v3_small(weights=WEIGHTS)
    input_features = model.classifier[-1].in_features
    model.classifier[-1] = torch.nn.Linear(input_features, len(CLOTHING_CATEGORIES))

    if MODEL_PATH.exists():
        try:
            checkpoint = torch.load(MODEL_PATH, map_location=DEVICE)
            state_dict = checkpoint.get("model_state_dict", checkpoint)
            model.load_state_dict(state_dict)
            print(f"[Classification] Loaded clothing weights from {MODEL_PATH}")
        except Exception as error:
            print(f"[Classification] Clothing weight load failed: {error}")
            print("[Classification] Falling back to pretrained MobileNetV3 features.")
    else:
        print(f"[Classification] No clothing weight file found at {MODEL_PATH}")
        print("[Classification] Falling back to pretrained MobileNetV3 features.")

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

    If fine-tuned clothing weights are unavailable, inference still runs with
    ImageNet-pretrained features and the replacement 8-class classifier head.
    """
    image = Image.open(BytesIO(image_bytes)).convert("RGB")
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

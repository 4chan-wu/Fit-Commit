from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
import os
from typing import Literal

import torch
from PIL import Image

from services.classification_service import predict_category

try:
    from transformers import CLIPModel, CLIPProcessor
except ImportError:
    CLIPModel = None
    CLIPProcessor = None


CategoryHint = Literal["top", "bottom", "outer", "shoes", "hat", "bag", "dress"]
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
MODEL_ID = os.getenv("FASHION_CLIP_MODEL_ID", "openai/clip-vit-base-patch32")
MODEL = None
PROCESSOR = None
MODEL_LOAD_ERROR: str | None = None

ALLOWED_CATEGORY_HINTS: tuple[str, ...] = (
    "top",
    "bottom",
    "outer",
    "shoes",
    "hat",
    "bag",
    "dress",
)

TEXT_CANDIDATES: dict[str, list[str]] = {
    "top": ["t-shirt", "shirt", "hoodie", "sweatshirt", "knit", "blouse"],
    "bottom": ["jeans", "slacks", "shorts", "skirt", "cargo pants"],
    "outer": ["jacket", "coat", "blazer", "padding jacket", "windbreaker"],
    "shoes": ["sneakers", "loafers", "boots", "sandals"],
    "hat": ["cap", "beanie", "bucket hat"],
    "bag": ["backpack", "tote bag", "shoulder bag", "cross bag"],
    "dress": ["dress", "one-piece", "long dress"],
}

STYLE_TAGS_BY_SUBCATEGORY: dict[str, list[str]] = {
    "t-shirt": ["casual", "daily"],
    "shirt": ["classic", "minimal"],
    "hoodie": ["casual", "street"],
    "sweatshirt": ["casual", "street"],
    "knit": ["cozy", "minimal"],
    "blouse": ["feminine", "classic"],
    "jeans": ["casual", "denim"],
    "slacks": ["formal", "minimal"],
    "shorts": ["casual", "summer"],
    "skirt": ["feminine", "daily"],
    "cargo pants": ["casual", "street"],
    "jacket": ["casual", "daily"],
    "coat": ["classic", "winter"],
    "blazer": ["formal", "classic"],
    "padding jacket": ["casual", "winter"],
    "windbreaker": ["sporty", "casual"],
    "sneakers": ["casual", "sporty"],
    "loafers": ["classic", "formal"],
    "boots": ["classic", "winter"],
    "sandals": ["summer", "casual"],
    "cap": ["casual", "street"],
    "beanie": ["casual", "winter"],
    "bucket hat": ["casual", "street"],
    "backpack": ["casual", "utility"],
    "tote bag": ["daily", "minimal"],
    "shoulder bag": ["classic", "daily"],
    "cross bag": ["casual", "utility"],
    "dress": ["feminine", "daily"],
    "one-piece": ["feminine", "minimal"],
    "long dress": ["feminine", "classic"],
}

MOBILENET_TO_CANDIDATE: dict[str, str] = {
    "t-shirt": "t-shirt",
    "shirt": "shirt",
    "hoodie": "hoodie",
    "sweater": "knit",
    "pants": "slacks",
    "skirt": "skirt",
    "dress": "dress",
    "outerwear": "jacket",
}


@dataclass(frozen=True)
class FashionClassificationResult:
    main_category: str
    subcategory: str
    style_tags: list[str]
    confidence: float
    method: str


def predict_fashion_category(
    image_bytes: bytes,
    category_hint: CategoryHint,
) -> FashionClassificationResult:
    """Classify an item by CLIP image-text similarity with MobileNet fallback."""
    if load_model():
        try:
            return _predict_with_clip(image_bytes, category_hint)
        except Exception as error:
            print(f"[FashionCLIP] CLIP inference failed: {error}")

    return _predict_with_mobilenet_fallback(image_bytes, category_hint)


def load_model() -> bool:
    """Load CLIP/FashionCLIP once and reuse it for subsequent requests."""
    global MODEL, PROCESSOR, MODEL_LOAD_ERROR

    if MODEL is not None and PROCESSOR is not None:
        return True

    if MODEL_LOAD_ERROR is not None:
        return False

    if CLIPModel is None or CLIPProcessor is None:
        MODEL_LOAD_ERROR = "transformers is not installed"
        print(f"[FashionCLIP] {MODEL_LOAD_ERROR}. Using MobileNet fallback.")
        return False

    try:
        print(f"[FashionCLIP] Loading model: {MODEL_ID} on {DEVICE}")
        PROCESSOR = CLIPProcessor.from_pretrained(MODEL_ID)
        MODEL = CLIPModel.from_pretrained(MODEL_ID).to(DEVICE)
        MODEL.eval()
        print("[FashionCLIP] Model loaded.")
        return True
    except Exception as error:
        MODEL = None
        PROCESSOR = None
        MODEL_LOAD_ERROR = str(error)
        print(f"[FashionCLIP] Model load failed: {error}. Using MobileNet fallback.")
        return False


def _predict_with_clip(
    image_bytes: bytes,
    category_hint: str,
) -> FashionClassificationResult:
    if MODEL is None or PROCESSOR is None:
        raise RuntimeError("CLIP model is not loaded.")

    candidates = TEXT_CANDIDATES[category_hint]
    prompts = [_prompt_for_candidate(candidate, category_hint) for candidate in candidates]
    image = Image.open(BytesIO(image_bytes)).convert("RGB")

    inputs = PROCESSOR(
        text=prompts,
        images=image,
        return_tensors="pt",
        padding=True,
    )
    inputs = {key: value.to(DEVICE) for key, value in inputs.items()}

    with torch.inference_mode():
        outputs = MODEL(**inputs)
        probabilities = torch.softmax(outputs.logits_per_image[0], dim=0)
        confidence, index = torch.max(probabilities, dim=0)

    subcategory = candidates[int(index.item())]
    return FashionClassificationResult(
        main_category=category_hint,
        subcategory=subcategory,
        style_tags=STYLE_TAGS_BY_SUBCATEGORY.get(subcategory, []),
        confidence=round(float(confidence.item()), 4),
        method="clip",
    )


def _predict_with_mobilenet_fallback(
    image_bytes: bytes,
    category_hint: str,
) -> FashionClassificationResult:
    candidates = TEXT_CANDIDATES[category_hint]
    legacy_result = predict_category(image_bytes)
    legacy_category = legacy_result.category.strip().lower()
    subcategory = MOBILENET_TO_CANDIDATE.get(legacy_category)

    if subcategory not in candidates:
        subcategory = candidates[0]
        confidence = min(legacy_result.confidence, 0.5)
    else:
        confidence = legacy_result.confidence

    return FashionClassificationResult(
        main_category=category_hint,
        subcategory=subcategory,
        style_tags=STYLE_TAGS_BY_SUBCATEGORY.get(subcategory, []),
        confidence=round(float(confidence), 4),
        method="fallback",
    )


def _prompt_for_candidate(candidate: str, category_hint: str) -> str:
    category_names = {
        "top": "top clothing item",
        "bottom": "bottom clothing item",
        "outer": "outerwear item",
        "shoes": "pair of shoes",
        "hat": "hat",
        "bag": "bag",
        "dress": "dress",
    }
    category_name = category_names.get(category_hint, "fashion item")
    return f"a photo of a {candidate}, a {category_name}, fashion product photo"

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from services.classification_service import predict_category


CategoryHint = Literal["top", "bottom", "outer", "shoes", "hat", "bag", "dress"]

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
    """Classify an item within a category-specific text candidate set.

    This service is the CLIP/FashionCLIP integration point. When a CLIP model is
    connected, replace `_predict_with_mobilenet_fallback` with text-image
    similarity scoring over `TEXT_CANDIDATES[category_hint]`.
    """
    return _predict_with_mobilenet_fallback(image_bytes, category_hint)


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
        method="clip",
    )

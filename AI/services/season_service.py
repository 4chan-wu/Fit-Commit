from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import os

import numpy as np
import torch
from PIL import Image
from torchvision import transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small


SEASON_CATEGORIES = ["Spring/Summer", "Fall/Winter", "All-Season"]
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
WEIGHTS = MobileNet_V3_Small_Weights.DEFAULT
DEFAULT_MODEL_PATH = (
    Path(__file__).resolve().parents[1] / "models" / "season_mobilenet_v3_small.pth"
)
MODEL_PATH = Path(os.getenv("SEASON_MODEL_PATH", DEFAULT_MODEL_PATH))
MODEL_AVAILABLE = MODEL_PATH.exists()
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


@dataclass(frozen=True)
class SeasonResult:
    season: str
    confidence: float
    method: str


def predict_season(image_bytes: bytes) -> SeasonResult:
    """Predict clothing season from image bytes.

    A fine-tuned model can be dropped in later. Until then, this function uses
    lightweight brightness/saturation heuristics that follow the same
    preprocess -> inference -> result shape.
    """
    image = Image.open(BytesIO(image_bytes)).convert("RGB")
    tensor = PREPROCESS(image).unsqueeze(0).to(DEVICE)

    if MODEL_AVAILABLE:
        with torch.inference_mode():
            logits = _get_model()(tensor)
            probabilities = torch.softmax(logits, dim=1)
            confidence, class_index = probabilities.max(dim=1)

        index = int(class_index.item())
        return SeasonResult(
            season=SEASON_CATEGORIES[index],
            confidence=round(float(confidence.item()), 4),
            method="model",
        )

    return _predict_with_image_heuristics(image)


def _build_model() -> torch.nn.Module:
    model = mobilenet_v3_small(weights=WEIGHTS)
    input_features = model.classifier[-1].in_features
    model.classifier[-1] = torch.nn.Linear(input_features, len(SEASON_CATEGORIES))

    try:
        checkpoint = torch.load(MODEL_PATH, map_location=DEVICE)
        state_dict = checkpoint.get("model_state_dict", checkpoint)
        model.load_state_dict(state_dict)
        print(f"[Season] Loaded season weights from {MODEL_PATH}")
    except Exception as error:
        print(f"[Season] Season weight load failed: {error}")
        print("[Season] Falling back to image heuristics.")

    model.to(DEVICE)
    model.eval()
    return model


def _get_model() -> torch.nn.Module:
    global MODEL

    if MODEL is None:
        MODEL = _build_model()

    return MODEL


def load_model() -> torch.nn.Module | None:
    """Load the season model at startup when weights exist; otherwise use heuristics."""
    if not MODEL_AVAILABLE:
        print(f"[Season] No season weight file found at {MODEL_PATH}")
        print("[Season] Using image heuristics.")
        return None

    return _get_model()


def _predict_with_image_heuristics(image: Image.Image) -> SeasonResult:
    rgb = np.asarray(image.resize((224, 224)), dtype=np.float32) / 255.0
    hsv = _rgb_to_hsv(rgb)

    brightness = float(hsv[..., 2].mean())
    saturation = float(hsv[..., 1].mean())
    hue = hsv[..., 0]

    warm_ratio = float(((hue < 0.12) | (hue > 0.92) | ((hue > 0.06) & (hue < 0.18))).mean())
    cool_ratio = float(((hue > 0.45) & (hue < 0.72)).mean())
    dark_ratio = float((hsv[..., 2] < 0.35).mean())

    spring_summer_score = (brightness * 0.45) + (saturation * 0.3) + (cool_ratio * 0.25)
    fall_winter_score = (dark_ratio * 0.45) + (warm_ratio * 0.35) + ((1.0 - brightness) * 0.2)
    score_gap = abs(spring_summer_score - fall_winter_score)

    if score_gap < 0.08:
        season = "All-Season"
        confidence = 1.0 - score_gap
    elif spring_summer_score > fall_winter_score:
        season = "Spring/Summer"
        confidence = spring_summer_score
    else:
        season = "Fall/Winter"
        confidence = fall_winter_score

    return SeasonResult(
        season=season,
        confidence=round(float(np.clip(confidence, 0.0, 1.0)), 4),
        method="heuristic",
    )


def _rgb_to_hsv(rgb: np.ndarray) -> np.ndarray:
    red = rgb[..., 0]
    green = rgb[..., 1]
    blue = rgb[..., 2]

    max_channel = rgb.max(axis=-1)
    min_channel = rgb.min(axis=-1)
    delta = max_channel - min_channel

    hue = np.zeros_like(max_channel)
    nonzero_delta = delta != 0

    red_is_max = (max_channel == red) & nonzero_delta
    green_is_max = (max_channel == green) & nonzero_delta
    blue_is_max = (max_channel == blue) & nonzero_delta

    hue[red_is_max] = ((green[red_is_max] - blue[red_is_max]) / delta[red_is_max]) % 6
    hue[green_is_max] = ((blue[green_is_max] - red[green_is_max]) / delta[green_is_max]) + 2
    hue[blue_is_max] = ((red[blue_is_max] - green[blue_is_max]) / delta[blue_is_max]) + 4
    hue /= 6

    saturation = np.zeros_like(max_channel)
    nonzero_value = max_channel != 0
    saturation[nonzero_value] = delta[nonzero_value] / max_channel[nonzero_value]

    return np.stack([hue, saturation, max_channel], axis=-1)

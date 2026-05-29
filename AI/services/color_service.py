from __future__ import annotations

import cv2
import numpy as np
from sklearn.cluster import KMeans


def extract_color(image_bytes: bytes) -> str:
    """Extract the dominant center-region color as a HEX string."""
    image_array = np.frombuffer(image_bytes, dtype=np.uint8)
    bgr_image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)

    if bgr_image is None:
        raise ValueError("이미지를 복호화 오류") ## 이미지를 OpenCV 형식으로 디코딩

    rgb_image = cv2.cvtColor(bgr_image, cv2.COLOR_BGR2RGB)
    center_region = _center_crop(rgb_image, crop_ratio=0.5)
    pixels = center_region.reshape(-1, 3)

    if pixels.size == 0:
        raise ValueError("색상 추출에 실패하였습니다.")

    cluster_count = min(3, len(pixels))
    kmeans = KMeans(n_clusters=cluster_count, n_init=10, random_state=42)
    labels = kmeans.fit_predict(pixels)
    dominant_cluster = int(np.bincount(labels).argmax())
    dominant_rgb = kmeans.cluster_centers_[dominant_cluster].astype(int)

    return _rgb_to_hex(dominant_rgb)


def _center_crop(rgb_image: np.ndarray, crop_ratio: float) -> np.ndarray:
    height, width, _ = rgb_image.shape
    crop_width = max(int(width * crop_ratio), 1)
    crop_height = max(int(height * crop_ratio), 1)
    start_x = max((width - crop_width) // 2, 0)
    start_y = max((height - crop_height) // 2, 0)
    return rgb_image[start_y : start_y + crop_height, start_x : start_x + crop_width]


def _rgb_to_hex(rgb: np.ndarray) -> str:
    red, green, blue = np.clip(rgb, 0, 255)
    return f"#{red:02X}{green:02X}{blue:02X}"

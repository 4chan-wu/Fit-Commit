from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import FastAPI, File, Form, HTTPException, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from services.classification_service import load_model as load_category_model
from services.color_service import extract_color
from services.fashion_clip_service import CategoryHint
from services.fashion_clip_service import predict_fashion_category
from services.season_service import load_model as load_season_model
from services.season_service import predict_season


@asynccontextmanager
async def lifespan(app: FastAPI):
    await asyncio.gather(
        asyncio.to_thread(load_category_model),
        asyncio.to_thread(load_season_model),
    )
    yield


class AnalysisPayload(BaseModel):
    main_category: str = Field(..., examples=["top"])
    subcategory: str = Field(..., examples=["hoodie"])
    main_color: str = Field(..., examples=["gray"])
    season: list[str] = Field(default_factory=list, examples=[["fall", "winter"]])
    style_tags: list[str] = Field(default_factory=list, examples=[["casual", "street"]])
    confidence: float = Field(..., ge=0, le=1, examples=[0.85])
    method: str = Field(..., examples=["clip"])


class PredictionResponse(BaseModel):
    status: str = Field(..., examples=["success"])
    analysis: AnalysisPayload


app = FastAPI(
    title="FitCommit Fashion AI Server",
    description="MVP API for clothing category prediction and dominant color extraction.",
    version="0.1.0",
    lifespan=lifespan,
    redirect_slashes=True,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/predict", response_model=PredictionResponse)
@app.post("/predict/", response_model=PredictionResponse, include_in_schema=False)
async def predict(
    file: Annotated[UploadFile, File(...)],
    category_hint: Annotated[CategoryHint, Form(...)],
) -> PredictionResponse:
    print("=== [Server] 요청 수신 대기 중... ===", flush=True)
    print(
        f"[Server] /predict entered: filename={file.filename!r}, content_type={file.content_type!r}, category_hint={category_hint!r}",
        flush=True,
    )

    if file.content_type is None or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="이미지 파일만 업로드할 수 있습니다.",
        )

    image_bytes = await file.read()
    print(f"[Server] Uploaded bytes: {len(image_bytes)}", flush=True)
    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드된 이미지가 없습니다.",
        )

    try:
        classification_task = asyncio.to_thread(
            predict_fashion_category,
            image_bytes,
            category_hint,
        )
        color_task = asyncio.to_thread(extract_color, image_bytes)
        season_task = asyncio.to_thread(predict_season, image_bytes)
        classification, dominant_color_hex, season = await asyncio.gather(
            classification_task,
            color_task,
            season_task,
        )
    except ValueError as error:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(error),
        ) from error
    except Exception as error:
        print(f"[Predict] Unexpected error: {error}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="서버 오류가 발생했습니다.",
        ) from error

    return PredictionResponse(
        status="success",
        analysis=AnalysisPayload(
            main_category=classification.main_category,
            subcategory=classification.subcategory,
            main_color=_hex_to_color_name(dominant_color_hex),
            season=_normalize_season(season.season),
            style_tags=classification.style_tags,
            confidence=classification.confidence,
            method=classification.method,
        ),
    )


def _normalize_season(season: str) -> list[str]:
    if season == "Spring/Summer":
        return ["spring", "summer"]
    if season == "Fall/Winter":
        return ["fall", "winter"]
    if season == "All-Season":
        return ["spring", "summer", "fall", "winter"]

    normalized = season.strip().lower()
    return [normalized] if normalized else []


def _hex_to_color_name(hex_color: str) -> str:
    hex_value = hex_color.strip().lstrip("#")
    if len(hex_value) != 6:
        return "unknown"

    try:
        red = int(hex_value[0:2], 16)
        green = int(hex_value[2:4], 16)
        blue = int(hex_value[4:6], 16)
    except ValueError:
        return "unknown"

    color_names = {
        "black": (0, 0, 0),
        "white": (255, 255, 255),
        "gray": (128, 128, 128),
        "red": (220, 40, 40),
        "orange": (235, 130, 40),
        "yellow": (230, 210, 50),
        "green": (50, 150, 80),
        "blue": (50, 100, 210),
        "navy": (20, 35, 90),
        "purple": (130, 80, 180),
        "pink": (230, 130, 170),
        "brown": (120, 80, 50),
        "beige": (210, 190, 150),
    }
    distances = {
        name: (red - rgb[0]) ** 2 + (green - rgb[1]) ** 2 + (blue - rgb[2]) ** 2
        for name, rgb in color_names.items()
    }
    return min(distances, key=distances.get)

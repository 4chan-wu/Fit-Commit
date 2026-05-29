from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import FastAPI, File, HTTPException, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from services.classification_service import load_model as load_category_model
from services.classification_service import predict_category
from services.color_service import extract_color
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
    category: str = Field(..., examples=["T-shirt"])
    confidence_cat: float = Field(..., ge=0, le=1, examples=[0.85])
    color: str = Field(..., pattern=r"^#[0-9A-Fa-f]{6}$", examples=["#1A237E"])
    season: str = Field(..., examples=["Spring/Summer"])
    confidence_sea: float = Field(..., ge=0, le=1, examples=[0.72])
    method_sea: str = Field(..., examples=["heuristic"])


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
async def predict(file: Annotated[UploadFile, File(...)]) -> PredictionResponse:
    print("=== [Server] 요청 수신 대기 중... ===", flush=True)
    print(
        f"[Server] /predict entered: filename={file.filename!r}, content_type={file.content_type!r}",
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
        classification_task = asyncio.to_thread(predict_category, image_bytes)
        color_task = asyncio.to_thread(extract_color, image_bytes)
        season_task = asyncio.to_thread(predict_season, image_bytes)
        classification, dominant_color, season = await asyncio.gather(
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
            category=classification.category,
            confidence_cat=classification.confidence,
            color=dominant_color,
            season=season.season,
            confidence_sea=season.confidence,
            method_sea=season.method,
        ),
    )

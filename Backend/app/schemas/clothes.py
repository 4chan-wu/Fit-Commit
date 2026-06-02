from __future__ import annotations

from datetime import datetime
from typing import Any, Optional, Union
from uuid import UUID

from pydantic import BaseModel, Field


class ClothesCreate(BaseModel):
    user_id: UUID
    main_category: str = Field(..., examples=["top"])
    subcategory: Optional[str] = Field(default=None, examples=["hoodie"])
    main_color: Optional[str] = Field(default=None, examples=["gray"])
    season: list[str] = Field(default_factory=list, examples=[["fall", "winter"]])
    style_tags: list[str] = Field(default_factory=list, examples=[["casual", "street"]])
    image_url: Optional[str] = Field(default=None, examples=["https://..."])
    image_path: Optional[str] = Field(default=None, examples=["user_id/file.jpg"])
    confidence: Optional[float] = Field(default=None, ge=0, le=1, examples=[0.82])
    analysis_method: Optional[str] = Field(default=None, examples=["clip"])
    memo: Optional[str] = ""


class ClothesUpdate(BaseModel):
    main_category: Optional[str] = None
    subcategory: Optional[str] = None
    main_color: Optional[str] = None
    season: Optional[list[str]] = None
    style_tags: Optional[list[str]] = None
    image_url: Optional[str] = None
    image_path: Optional[str] = None
    confidence: Optional[float] = Field(default=None, ge=0, le=1)
    analysis_method: Optional[str] = None
    memo: Optional[str] = None


class ClothesResponse(ClothesCreate):
    id: Any
    created_at: Optional[Union[datetime, str]] = None
    updated_at: Optional[Union[datetime, str]] = None

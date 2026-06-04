from __future__ import annotations

from datetime import datetime
from typing import Optional, Union
from uuid import UUID

from pydantic import BaseModel, Field


class UserProfileCreate(BaseModel):
    user_id: Optional[UUID] = None
    nickname: Optional[str] = None
    height_cm: Optional[float] = Field(default=None, ge=0)
    body_type: Optional[str] = None
    preferred_style: list[str] = Field(default_factory=list)


class UserProfileUpdate(BaseModel):
    nickname: Optional[str] = None
    height_cm: Optional[float] = Field(default=None, ge=0)
    body_type: Optional[str] = None
    preferred_style: Optional[list[str]] = None


class UserProfileResponse(BaseModel):
    user_id: UUID
    nickname: Optional[str] = None
    height_cm: Optional[float] = None
    body_type: Optional[str] = None
    preferred_style: list[str] = Field(default_factory=list)
    created_at: Optional[Union[datetime, str]] = None
    updated_at: Optional[Union[datetime, str]] = None


class WardrobeResponse(BaseModel):
    id: UUID
    user_id: UUID
    name: str = "기본 옷장"
    created_at: Optional[Union[datetime, str]] = None
    updated_at: Optional[Union[datetime, str]] = None

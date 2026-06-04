from __future__ import annotations

from datetime import datetime
from typing import Optional, Union
from uuid import UUID

from pydantic import BaseModel


class WardrobeCreate(BaseModel):
    user_id: Optional[UUID] = None
    name: str = "기본 옷장"
    description: Optional[str] = None
    is_default: bool = False


class WardrobeUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    is_default: Optional[bool] = None


class WardrobeResponse(BaseModel):
    id: UUID
    user_id: UUID
    name: str
    description: Optional[str] = None
    is_default: bool = False
    created_at: Optional[Union[datetime, str]] = None
    updated_at: Optional[Union[datetime, str]] = None

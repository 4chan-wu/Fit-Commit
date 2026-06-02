from __future__ import annotations

from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Query

from app.schemas.clothes import ClothesCreate, ClothesResponse, ClothesUpdate
from app.services.supabase_clothes_service import clothes_service


router = APIRouter(prefix="/clothes", tags=["clothes"])


@router.post("", response_model=ClothesResponse)
async def create_clothing(body: ClothesCreate):
    return await clothes_service.create(body)


@router.get("", response_model=list[ClothesResponse])
async def list_clothes(user_id: UUID = Query(...)):
    return await clothes_service.list_by_user(user_id)


@router.get("/{clothing_id}", response_model=ClothesResponse)
async def get_clothing(
    clothing_id: str,
    user_id: Optional[UUID] = Query(default=None),
):
    return await clothes_service.get(clothing_id, user_id=user_id)


@router.put("/{clothing_id}", response_model=ClothesResponse)
async def update_clothing(
    clothing_id: str,
    body: ClothesUpdate,
    user_id: Optional[UUID] = Query(default=None),
):
    return await clothes_service.update(clothing_id, body, user_id=user_id)


@router.delete("/{clothing_id}")
async def delete_clothing(
    clothing_id: str,
    user_id: Optional[UUID] = Query(default=None),
):
    return await clothes_service.delete(clothing_id, user_id=user_id)

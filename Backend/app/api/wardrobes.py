from __future__ import annotations

from typing import Optional
from uuid import UUID

from fastapi import APIRouter, HTTPException, Query, Request, status

from app.schemas.wardrobes import WardrobeCreate, WardrobeResponse, WardrobeUpdate
from app.services.supabase_auth_service import user_resolver
from app.services.supabase_wardrobe_service import wardrobe_service


router = APIRouter(prefix="/wardrobes", tags=["wardrobes"])


@router.get("", response_model=list[WardrobeResponse])
async def list_wardrobes(request: Request, user_id: UUID = Query(...)):
    user_id = await _resolve_requested_user(request, user_id)
    return await wardrobe_service.list_by_user(user_id)


@router.post("", response_model=WardrobeResponse)
async def create_wardrobe(body: WardrobeCreate, request: Request):
    user_id = await _resolve_requested_user(request, body.user_id)
    return await wardrobe_service.create(user_id, body)


@router.get("/default", response_model=WardrobeResponse)
async def get_default_wardrobe(
    request: Request,
    user_id: Optional[UUID] = Query(default=None),
    create_if_missing: bool = Query(default=True),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    return await wardrobe_service.get_default(resolved_user_id, create_if_missing=create_if_missing)


@router.get("/me", response_model=list[WardrobeResponse])
async def list_my_wardrobes(request: Request):
    user_id = await user_resolver.resolve_user_id(request)
    return await wardrobe_service.list_by_user(user_id)


@router.post("/me", response_model=WardrobeResponse)
async def create_my_wardrobe(body: WardrobeCreate, request: Request):
    user_id = await user_resolver.resolve_user_id(request)
    return await wardrobe_service.create(user_id, body)


@router.get("/me/default", response_model=WardrobeResponse)
async def get_my_default_wardrobe(request: Request, create_if_missing: bool = Query(default=True)):
    user_id = await user_resolver.resolve_user_id(request)
    return await wardrobe_service.get_default(user_id, create_if_missing=create_if_missing)


@router.put("/{wardrobe_id}", response_model=WardrobeResponse)
async def update_wardrobe(
    wardrobe_id: UUID,
    body: WardrobeUpdate,
    request: Request,
    user_id: Optional[UUID] = Query(default=None),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    return await wardrobe_service.update(wardrobe_id, resolved_user_id, body)


@router.delete("/{wardrobe_id}")
async def delete_wardrobe(
    wardrobe_id: UUID,
    request: Request,
    user_id: Optional[UUID] = Query(default=None),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    return await wardrobe_service.delete(wardrobe_id, resolved_user_id)


async def _resolve_requested_user(request: Request, requested_user_id: Optional[UUID]) -> UUID:
    authenticated_user_id = await user_resolver.resolve_user_id(request)
    if requested_user_id is not None and requested_user_id != authenticated_user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Requested user_id does not match the authenticated user.",
        )
    return authenticated_user_id

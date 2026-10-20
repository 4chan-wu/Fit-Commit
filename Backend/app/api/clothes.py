from __future__ import annotations

from typing import Optional
from uuid import UUID

from fastapi import APIRouter, File, Form, HTTPException, Query, Request, UploadFile, status

from app.schemas.clothes import ClothesCreate, ClothesImageUploadResponse, ClothesResponse, ClothesUpdate
from app.services.supabase_auth_service import user_resolver
from app.services.supabase_clothes_service import clothes_service


router = APIRouter(prefix="/clothes", tags=["clothes"])


@router.post("", response_model=ClothesResponse)
async def create_clothing(body: ClothesCreate, request: Request):
    user_id = await _resolve_requested_user(request, body.user_id)
    body = body.model_copy(update={"user_id": user_id})
    return await clothes_service.create(body)


@router.post("/upload", response_model=ClothesImageUploadResponse)
async def upload_clothing_image(
    request: Request,
    file: UploadFile = File(...),
    main_category: str = Form(...),
    user_id: Optional[UUID] = Form(default=None),
    image_type: str = Form(default="original"),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    content = await file.read()
    return await clothes_service.upload_image(
        file_bytes=content,
        content_type=file.content_type or "image/jpeg",
        user_id=resolved_user_id,
        main_category=main_category,
        image_type=image_type,
    )


@router.get("", response_model=list[ClothesResponse])
async def list_clothes(
    request: Request,
    user_id: UUID = Query(...),
    wardrobe_id: Optional[UUID] = Query(default=None),
):
    user_id = await _resolve_requested_user(request, user_id)
    if wardrobe_id is not None:
        return await clothes_service.list_by_wardrobe(wardrobe_id, user_id=user_id)
    return await clothes_service.list_by_user(user_id)


@router.get("/me", response_model=list[ClothesResponse])
async def list_my_clothes(request: Request, wardrobe_id: Optional[UUID] = Query(default=None)):
    user_id = await user_resolver.resolve_user_id(request)
    if wardrobe_id is not None:
        return await clothes_service.list_by_wardrobe(wardrobe_id, user_id=user_id)
    return await clothes_service.list_by_user(user_id)


@router.get("/{clothing_id}", response_model=ClothesResponse)
async def get_clothing(
    clothing_id: str,
    request: Request,
    user_id: Optional[UUID] = Query(default=None),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    return await clothes_service.get(clothing_id, user_id=resolved_user_id)


@router.put("/{clothing_id}", response_model=ClothesResponse)
async def update_clothing(
    clothing_id: str,
    body: ClothesUpdate,
    request: Request,
    user_id: Optional[UUID] = Query(default=None),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    return await clothes_service.update(clothing_id, body, user_id=resolved_user_id)


@router.post("/{clothing_id}/analyze")
async def analyze_clothing(
    clothing_id: str,
    request: Request,
    user_id: Optional[UUID] = Query(default=None),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    row = await clothes_service.get(clothing_id, user_id=resolved_user_id)
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=(
            "Re-analysis endpoint is reserved. "
            f"clothing_id={row.get('id')} has image_url/image_path, but AI 재전송 구조와 "
            "Supabase Storage download flow are not implemented yet."
        ),
    )


@router.delete("/{clothing_id}")
async def delete_clothing(
    clothing_id: str,
    request: Request,
    user_id: Optional[UUID] = Query(default=None),
):
    resolved_user_id = await _resolve_requested_user(request, user_id)
    return await clothes_service.delete(clothing_id, user_id=resolved_user_id)


async def _resolve_requested_user(request: Request, requested_user_id: Optional[UUID]) -> UUID:
    authenticated_user_id = await user_resolver.resolve_user_id(request)
    if requested_user_id is not None and requested_user_id != authenticated_user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Requested user_id does not match the authenticated user.",
        )
    return authenticated_user_id

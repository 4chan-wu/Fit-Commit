from __future__ import annotations

from fastapi import APIRouter, Request

from app.schemas.profiles import UserProfileCreate, UserProfileResponse, UserProfileUpdate, WardrobeResponse
from app.services.supabase_auth_service import user_resolver
from app.services.supabase_profile_service import profile_service


router = APIRouter(prefix="/users/me", tags=["user_profiles"])


@router.get("/profile", response_model=UserProfileResponse)
async def get_my_profile(request: Request):
    user_id = await user_resolver.resolve_user_id(request)
    return await profile_service.get_profile(user_id)


@router.post("/profile", response_model=UserProfileResponse)
async def create_my_profile(body: UserProfileCreate, request: Request):
    user_id = await user_resolver.resolve_user_id(request)
    return await profile_service.upsert_profile(user_id, body)


@router.put("/profile", response_model=UserProfileResponse)
async def update_my_profile(body: UserProfileUpdate, request: Request):
    user_id = await user_resolver.resolve_user_id(request)
    return await profile_service.update_profile(user_id, body)


@router.get("/wardrobes", response_model=list[WardrobeResponse])
async def list_my_wardrobes(request: Request):
    user_id = await user_resolver.resolve_user_id(request)
    return await profile_service.list_wardrobes(user_id)

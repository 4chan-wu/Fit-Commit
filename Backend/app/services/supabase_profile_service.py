from __future__ import annotations

import logging
import os
from typing import Any
from uuid import UUID

import httpx
from fastapi import HTTPException, status

from app.schemas.profiles import UserProfileCreate, UserProfileUpdate
from app.services.supabase_wardrobe_service import wardrobe_service


logger = logging.getLogger(__name__)


class SupabaseProfileService:
    def __init__(self) -> None:
        self.supabase_url = os.getenv("SUPABASE_URL", "").rstrip("/")
        self.service_role_key = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "")

    @property
    def is_configured(self) -> bool:
        return bool(self.supabase_url and self.service_role_key)

    def _headers(self) -> dict[str, str]:
        if not self.is_configured:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Supabase profile API is not configured.",
            )
        return {
            "apikey": self.service_role_key,
            "Authorization": f"Bearer {self.service_role_key}",
            "Content-Type": "application/json",
        }

    def _table_url(self, table: str) -> str:
        return f"{self.supabase_url}/rest/v1/{table}"

    async def get_profile(self, user_id: UUID) -> dict[str, Any]:
        response = await self._request(
            "GET",
            self._table_url("user_profiles"),
            headers=self._headers(),
            params={"user_id": f"eq.{user_id}", "limit": "1"},
        )
        return self._single_row_or_404(response, f"profile user_id={user_id}")

    async def upsert_profile(self, user_id: UUID, body: UserProfileCreate) -> dict[str, Any]:
        payload = body.model_dump(mode="json", exclude_none=True)
        payload["user_id"] = str(user_id)
        payload.setdefault("preferred_style", [])

        response = await self._request(
            "POST",
            self._table_url("user_profiles"),
            headers={
                **self._headers(),
                "Prefer": "return=representation,resolution=merge-duplicates",
            },
            params={"on_conflict": "user_id"},
            json=payload,
        )
        profile = self._single_row(response)
        await wardrobe_service.get_default(user_id, create_if_missing=True)
        logger.info("[Profiles] Upserted profile user_id=%s", user_id)
        return profile

    async def update_profile(self, user_id: UUID, body: UserProfileUpdate) -> dict[str, Any]:
        payload = body.model_dump(mode="json", exclude_unset=True, exclude_none=True)
        if not payload:
            raise HTTPException(status_code=400, detail="No profile update fields provided.")

        response = await self._request(
            "PATCH",
            self._table_url("user_profiles"),
            headers={**self._headers(), "Prefer": "return=representation"},
            params={"user_id": f"eq.{user_id}"},
            json=payload,
        )
        profile = self._single_row_or_404(response, f"profile user_id={user_id}")
        logger.info("[Profiles] Updated profile user_id=%s fields=%s", user_id, ",".join(payload.keys()))
        return profile

    async def ensure_default_wardrobe(self, user_id: UUID) -> dict[str, Any]:
        return await wardrobe_service.get_default(user_id, create_if_missing=True)

    async def list_wardrobes(self, user_id: UUID) -> list[dict[str, Any]]:
        return await wardrobe_service.list_by_user(user_id)

    async def _request(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                response = await client.request(method, url, **kwargs)
        except httpx.HTTPError as error:
            logger.exception("[Profiles] Supabase request failed: %s", error)
            raise HTTPException(status_code=502, detail="Supabase profile request failed.") from error

        if response.status_code >= 400:
            logger.error("[Profiles] Supabase error status=%s body=%s", response.status_code, response.text[:1000])
            raise HTTPException(status_code=response.status_code, detail=response.text)

        return response

    def _single_row(self, response: httpx.Response) -> dict[str, Any]:
        rows = response.json()
        if not rows:
            raise HTTPException(status_code=500, detail="Supabase returned no row.")
        return rows[0]

    def _single_row_or_404(self, response: httpx.Response, label: str) -> dict[str, Any]:
        rows = response.json()
        if not rows:
            raise HTTPException(status_code=404, detail=f"Not found: {label}")
        return rows[0]


profile_service = SupabaseProfileService()

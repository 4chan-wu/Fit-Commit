from __future__ import annotations

import logging
import os
from typing import Any, Optional
from uuid import UUID

import httpx
from fastapi import HTTPException, status

from app.schemas.wardrobes import WardrobeCreate, WardrobeUpdate


logger = logging.getLogger(__name__)


class SupabaseWardrobeService:
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
                detail="Supabase wardrobe API is not configured.",
            )
        return {
            "apikey": self.service_role_key,
            "Authorization": f"Bearer {self.service_role_key}",
            "Content-Type": "application/json",
        }

    def _table_url(self) -> str:
        return f"{self.supabase_url}/rest/v1/wardrobes"

    async def list_by_user(self, user_id: UUID) -> list[dict[str, Any]]:
        response = await self._request(
            "GET",
            self._table_url(),
            headers=self._headers(),
            params={"user_id": f"eq.{user_id}", "order": "is_default.desc,created_at.asc"},
        )
        rows = response.json()
        logger.info("[Wardrobes] Listed %s wardrobes user_id=%s", len(rows), user_id)
        return rows

    async def get_default(self, user_id: UUID, create_if_missing: bool = True) -> dict[str, Any]:
        response = await self._request(
            "GET",
            self._table_url(),
            headers=self._headers(),
            params={"user_id": f"eq.{user_id}", "is_default": "eq.true", "limit": "1"},
        )
        rows = response.json()
        if rows:
            return rows[0]
        if not create_if_missing:
            raise HTTPException(status_code=404, detail=f"Default wardrobe not found for user_id={user_id}")
        return await self.create(user_id, WardrobeCreate(name="기본 옷장", is_default=True))

    async def get_owned(self, wardrobe_id: UUID, user_id: UUID) -> dict[str, Any]:
        response = await self._request(
            "GET",
            self._table_url(),
            headers=self._headers(),
            params={"id": f"eq.{wardrobe_id}", "user_id": f"eq.{user_id}", "limit": "1"},
        )
        return self._single_row_or_404(response, f"wardrobe_id={wardrobe_id}")

    async def ensure_owned_or_default(self, user_id: UUID, wardrobe_id: Optional[UUID]) -> dict[str, Any]:
        if wardrobe_id is not None:
            return await self.get_owned(wardrobe_id, user_id)
        return await self.get_default(user_id, create_if_missing=True)

    async def create(self, user_id: UUID, body: WardrobeCreate) -> dict[str, Any]:
        if body.is_default:
            existing_default = await self._find_default(user_id)
            if existing_default is not None:
                return existing_default

        payload = body.model_dump(mode="json", exclude_none=True)
        payload["user_id"] = str(user_id)
        payload.setdefault("name", "기본 옷장" if body.is_default else "새 옷장")
        payload.setdefault("is_default", False)

        response = await self._request(
            "POST",
            self._table_url(),
            headers={**self._headers(), "Prefer": "return=representation"},
            json=payload,
        )
        wardrobe = self._single_row(response)
        logger.info(
            "[Wardrobes] Created wardrobe id=%s user_id=%s is_default=%s",
            wardrobe.get("id"),
            user_id,
            wardrobe.get("is_default"),
        )
        return wardrobe

    async def update(self, wardrobe_id: UUID, user_id: UUID, body: WardrobeUpdate) -> dict[str, Any]:
        payload = body.model_dump(mode="json", exclude_unset=True, exclude_none=True)
        if not payload:
            raise HTTPException(status_code=400, detail="No wardrobe update fields provided.")

        existing = await self.get_owned(wardrobe_id, user_id)
        if existing.get("is_default") is True and payload.get("is_default") is False:
            raise HTTPException(status_code=400, detail="Default wardrobe cannot be unset directly.")
        if payload.get("is_default") is True:
            current_default = await self._find_default(user_id)
            if current_default is not None and str(current_default.get("id")) != str(wardrobe_id):
                raise HTTPException(status_code=409, detail="User already has a default wardrobe.")

        response = await self._request(
            "PATCH",
            self._table_url(),
            headers={**self._headers(), "Prefer": "return=representation"},
            params={"id": f"eq.{wardrobe_id}", "user_id": f"eq.{user_id}"},
            json=payload,
        )
        wardrobe = self._single_row_or_404(response, f"wardrobe_id={wardrobe_id}")
        logger.info("[Wardrobes] Updated wardrobe id=%s fields=%s", wardrobe_id, ",".join(payload.keys()))
        return wardrobe

    async def delete(self, wardrobe_id: UUID, user_id: UUID) -> dict[str, str]:
        existing = await self.get_owned(wardrobe_id, user_id)
        if existing.get("is_default") is True:
            raise HTTPException(status_code=400, detail="Default wardrobe cannot be deleted.")

        response = await self._request(
            "DELETE",
            self._table_url(),
            headers={**self._headers(), "Prefer": "return=representation"},
            params={"id": f"eq.{wardrobe_id}", "user_id": f"eq.{user_id}"},
        )
        self._single_row_or_404(response, f"wardrobe_id={wardrobe_id}")
        logger.info("[Wardrobes] Deleted wardrobe id=%s", wardrobe_id)
        return {"status": "deleted", "id": str(wardrobe_id)}

    async def _find_default(self, user_id: UUID) -> Optional[dict[str, Any]]:
        response = await self._request(
            "GET",
            self._table_url(),
            headers=self._headers(),
            params={"user_id": f"eq.{user_id}", "is_default": "eq.true", "limit": "1"},
        )
        rows = response.json()
        return rows[0] if rows else None

    async def _request(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                response = await client.request(method, url, **kwargs)
        except httpx.HTTPError as error:
            logger.exception("[Wardrobes] Supabase request failed: %s", error)
            raise HTTPException(status_code=502, detail="Supabase wardrobe request failed.") from error

        if response.status_code >= 400:
            logger.error("[Wardrobes] Supabase error status=%s body=%s", response.status_code, response.text[:1000])
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


wardrobe_service = SupabaseWardrobeService()

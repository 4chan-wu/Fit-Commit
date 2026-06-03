from __future__ import annotations

import logging
import os
from typing import Any, Optional
from uuid import UUID

import httpx
from fastapi import HTTPException, status

from app.schemas.clothes import ClothesCreate, ClothesUpdate


logger = logging.getLogger(__name__)


class SupabaseClothesService:
    def __init__(self) -> None:
        self.supabase_url = os.getenv("SUPABASE_URL", "").rstrip("/")
        self.service_role_key = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "")
        self.anon_key = os.getenv("SUPABASE_ANON_KEY", "")
        self.bucket = os.getenv("SUPABASE_CLOTHING_BUCKET", "clothing-images")

    @property
    def is_configured(self) -> bool:
        return bool(self.supabase_url and self.service_role_key)

    def _headers(self) -> dict[str, str]:
        if not self.is_configured:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Supabase clothes API is not configured.",
            )

        return {
            "apikey": self.service_role_key,
            "Authorization": f"Bearer {self.service_role_key}",
            "Content-Type": "application/json",
        }

    def _table_url(self) -> str:
        return f"{self.supabase_url}/rest/v1/clothes"

    async def create(self, body: ClothesCreate) -> dict[str, Any]:
        payload = body.model_dump(mode="json", exclude_none=True)
        logger.info(
            "[Clothes] Creating clothing item user_id=%s category=%s subcategory=%s analysis_status=%s",
            body.user_id,
            body.main_category,
            body.subcategory,
            body.analysis_status,
        )

        response = await self._request(
            "POST",
            self._table_url(),
            headers={**self._headers(), "Prefer": "return=representation"},
            json=payload,
        )
        row = self._single_row(response)
        logger.info("[Clothes] Created clothing item id=%s user_id=%s", row.get("id"), body.user_id)
        return row

    async def list_by_user(self, user_id: UUID) -> list[dict[str, Any]]:
        response = await self._request(
            "GET",
            self._table_url(),
            headers=self._headers(),
            params={
                "user_id": f"eq.{user_id}",
                "order": "created_at.desc",
            },
        )
        rows = response.json()
        logger.info("[Clothes] Listed %s clothing items user_id=%s", len(rows), user_id)
        return rows

    async def get(self, clothing_id: str, user_id: Optional[UUID] = None) -> dict[str, Any]:
        params = {"id": f"eq.{clothing_id}", "limit": "1"}
        if user_id is not None:
            params["user_id"] = f"eq.{user_id}"

        response = await self._request(
            "GET",
            self._table_url(),
            headers=self._headers(),
            params=params,
        )
        return self._single_row_or_404(response, clothing_id)

    async def update(
        self,
        clothing_id: str,
        body: ClothesUpdate,
        user_id: Optional[UUID] = None,
    ) -> dict[str, Any]:
        payload = body.model_dump(mode="json", exclude_unset=True, exclude_none=True)
        if not payload:
            raise HTTPException(status_code=400, detail="No update fields provided.")

        params = {"id": f"eq.{clothing_id}"}
        if user_id is not None:
            params["user_id"] = f"eq.{user_id}"

        response = await self._request(
            "PATCH",
            self._table_url(),
            headers={**self._headers(), "Prefer": "return=representation"},
            params=params,
            json=payload,
        )
        row = self._single_row_or_404(response, clothing_id)
        logger.info(
            "[Clothes] Updated clothing item id=%s fields=%s",
            clothing_id,
            ",".join(sorted(payload.keys())),
        )
        return row

    async def delete(self, clothing_id: str, user_id: Optional[UUID] = None) -> dict[str, str]:
        params = {"id": f"eq.{clothing_id}"}
        if user_id is not None:
            params["user_id"] = f"eq.{user_id}"

        response = await self._request(
            "DELETE",
            self._table_url(),
            headers={**self._headers(), "Prefer": "return=representation"},
            params=params,
        )
        self._single_row_or_404(response, clothing_id)
        logger.info("[Clothes] Deleted clothing item id=%s", clothing_id)
        return {"status": "deleted", "id": clothing_id}

    async def _request(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                response = await client.request(method, url, **kwargs)
        except httpx.HTTPError as error:
            logger.exception("[Clothes] Supabase request failed: %s", error)
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Supabase clothes request failed.",
            ) from error

        if response.status_code >= 400:
            logger.error(
                "[Clothes] Supabase error status=%s body=%s",
                response.status_code,
                response.text[:1000],
            )
            raise HTTPException(
                status_code=response.status_code,
                detail=response.text,
            )

        return response

    def _single_row(self, response: httpx.Response) -> dict[str, Any]:
        rows = response.json()
        if not rows:
            raise HTTPException(status_code=500, detail="Supabase returned no row.")
        return rows[0]

    def _single_row_or_404(self, response: httpx.Response, clothing_id: str) -> dict[str, Any]:
        rows = response.json()
        if not rows:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Clothing item not found: {clothing_id}",
            )
        return rows[0]


clothes_service = SupabaseClothesService()

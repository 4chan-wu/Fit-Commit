from __future__ import annotations

import logging
import os
from datetime import datetime, timezone
from typing import Any, Optional
from uuid import UUID, uuid4

import httpx
from fastapi import HTTPException, status

from app.schemas.clothes import ClothesCreate, ClothesUpdate
from app.services.supabase_wardrobe_service import wardrobe_service


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

    def _storage_object_url(self, object_path: str) -> str:
        return f"{self.supabase_url}/storage/v1/object/{self.bucket}/{object_path}"

    def _storage_public_url(self, object_path: str) -> str:
        return f"{self.supabase_url}/storage/v1/object/public/{self.bucket}/{object_path}"

    async def create(self, body: ClothesCreate) -> dict[str, Any]:
        if body.user_id is None:
            raise HTTPException(status_code=400, detail="user_id is required.")
        wardrobe = await wardrobe_service.ensure_owned_or_default(body.user_id, body.wardrobe_id)
        body = body.model_copy(update={"wardrobe_id": wardrobe.get("id")})
        payload = body.model_dump(mode="json", exclude_none=True)
        self._normalize_image_fields(payload)
        payload["sync_status"] = "synced"
        logger.info(
            "[Clothes] Creating clothing item user_id=%s category=%s subcategory=%s analysis_status=%s sync_status=%s",
            body.user_id,
            body.main_category,
            body.subcategory,
            body.analysis_status,
            payload["sync_status"],
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

    async def upload_image(
        self,
        file_bytes: bytes,
        content_type: str,
        user_id: UUID,
        main_category: str,
    ) -> dict[str, str]:
        if not self.is_configured:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Supabase Storage upload is not configured.",
            )
        if not file_bytes:
            raise HTTPException(status_code=400, detail="Uploaded file is empty.")

        object_path = self._build_storage_path(user_id, main_category)
        upload_headers = {
            "apikey": self.service_role_key,
            "Authorization": f"Bearer {self.service_role_key}",
            "Content-Type": content_type or "image/jpeg",
            "Cache-Control": "3600",
        }
        response = await self._request(
            "POST",
            self._storage_object_url(object_path),
            headers=upload_headers,
            content=file_bytes,
        )
        logger.info(
            "[Clothes] Uploaded clothing image bucket=%s path=%s status=%s",
            self.bucket,
            object_path,
            response.status_code,
        )
        return {
            "image_path": object_path,
            "image_url": self._storage_public_url(object_path),
            "bucket": self.bucket,
        }

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

    async def list_by_wardrobe(self, wardrobe_id: UUID, user_id: Optional[UUID] = None) -> list[dict[str, Any]]:
        params = {
            "wardrobe_id": f"eq.{wardrobe_id}",
            "order": "created_at.desc",
        }
        if user_id is not None:
            params["user_id"] = f"eq.{user_id}"

        response = await self._request(
            "GET",
            self._table_url(),
            headers=self._headers(),
            params=params,
        )
        rows = response.json()
        logger.info("[Clothes] Listed %s clothing items wardrobe_id=%s", len(rows), wardrobe_id)
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
        self._normalize_image_fields(payload)
        if not payload:
            raise HTTPException(status_code=400, detail="No update fields provided.")
        if body.wardrobe_id is not None and user_id is not None:
            await wardrobe_service.get_owned(body.wardrobe_id, user_id)
        payload["updated_at"] = datetime.now(timezone.utc).isoformat()

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

    def _build_storage_path(self, user_id: UUID, main_category: str) -> str:
        safe_category = "".join(
            character for character in main_category.lower() if character.isalnum() or character in ("-", "_")
        )
        if not safe_category:
            safe_category = "unknown"
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
        return f"{user_id}/{safe_category}/{timestamp}-{uuid4().hex}.jpg"

    def _normalize_image_fields(self, payload: dict[str, Any]) -> None:
        image_path = payload.get("image_path")
        if isinstance(image_path, str) and image_path.startswith("local://"):
            payload.setdefault("local_image_path", image_path)
            payload.pop("image_path", None)

        image_url = payload.get("image_url")
        if isinstance(image_url, str) and image_url.startswith("local://"):
            payload.pop("image_url", None)

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

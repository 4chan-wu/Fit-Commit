from __future__ import annotations

import logging
import os
from typing import Optional
from uuid import UUID

import httpx
from fastapi import HTTPException, Request, status


logger = logging.getLogger(__name__)


class SupabaseUserResolver:
    def __init__(self) -> None:
        self.supabase_url = os.getenv("SUPABASE_URL", "").rstrip("/")
        self.anon_key = os.getenv("SUPABASE_ANON_KEY", "")
        self.allow_test_user_id = os.getenv("ALLOW_TEST_USER_ID", "false").lower() == "true"

    def _auth_headers(self) -> dict[str, str]:
        if not self.supabase_url or not self.anon_key:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Supabase Auth is not configured.",
            )
        return {"apikey": self.anon_key, "Content-Type": "application/json"}

    async def sign_up(self, email: str, password: str, metadata: dict) -> dict:
        return await self._auth_request(
            "POST",
            "/auth/v1/signup",
            json={"email": email, "password": password, "data": metadata},
        )

    async def sign_in_with_password(self, email: str, password: str) -> dict:
        return await self._auth_request(
            "POST",
            "/auth/v1/token",
            params={"grant_type": "password"},
            json={"email": email, "password": password},
        )

    async def refresh_session(self, refresh_token: str) -> dict:
        return await self._auth_request(
            "POST",
            "/auth/v1/token",
            params={"grant_type": "refresh_token"},
            json={"refresh_token": refresh_token},
        )

    async def _auth_request(self, method: str, path: str, **kwargs) -> dict:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                response = await client.request(
                    method,
                    f"{self.supabase_url}{path}",
                    headers=self._auth_headers(),
                    **kwargs,
                )
        except httpx.HTTPError as error:
            logger.exception("[Auth] Supabase request failed: %s", error)
            raise HTTPException(status_code=502, detail="Supabase Auth request failed.") from error

        if response.status_code >= 400:
            try:
                error_body = response.json()
                detail = (
                    error_body.get("msg")
                    or error_body.get("error_description")
                    or error_body.get("message")
                    or "Supabase authentication failed."
                )
            except ValueError:
                detail = "Supabase authentication failed."
            raise HTTPException(status_code=response.status_code, detail=detail)

        return response.json()

    async def resolve_user_id(self, request: Request) -> UUID:
        token_user_id = await self._user_id_from_authorization(request)
        if token_user_id is not None:
            return token_user_id

        test_user_id = request.headers.get("X-Test-User-Id") or request.query_params.get("user_id")
        if self.allow_test_user_id and test_user_id:
            return self._parse_uuid(test_user_id, "Invalid X-Test-User-Id/user_id value.")

        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=(
                "Authorization Bearer token or X-Test-User-Id is required."
                if self.allow_test_user_id
                else "Authorization Bearer token is required."
            ),
        )

    async def _user_id_from_authorization(self, request: Request) -> Optional[UUID]:
        authorization = request.headers.get("Authorization", "")
        prefix = "Bearer "
        if not authorization.startswith(prefix):
            return None

        token = authorization[len(prefix) :].strip()
        if not token:
            return None
        if not self.supabase_url or not self.anon_key:
            logger.warning("[Auth] SUPABASE_URL/SUPABASE_ANON_KEY missing; cannot resolve Bearer token.")
            return None

        try:
            async with httpx.AsyncClient(timeout=10) as client:
                response = await client.get(
                    f"{self.supabase_url}/auth/v1/user",
                    headers={
                        "Authorization": f"Bearer {token}",
                        "apikey": self.anon_key,
                    },
                )
        except httpx.HTTPError as error:
            logger.exception("[Auth] Supabase Auth user lookup failed: %s", error)
            raise HTTPException(status_code=502, detail="Supabase Auth user lookup failed.") from error

        if response.status_code >= 400:
            logger.warning("[Auth] Invalid Supabase access token status=%s", response.status_code)
            raise HTTPException(status_code=401, detail="Invalid Supabase access token.")

        user_id = response.json().get("id")
        if not user_id:
            raise HTTPException(status_code=401, detail="Supabase access token has no user id.")
        return self._parse_uuid(user_id, "Invalid Supabase Auth user id.")

    def _parse_uuid(self, value: str, detail: str) -> UUID:
        try:
            return UUID(value)
        except ValueError as error:
            raise HTTPException(status_code=400, detail=detail) from error


user_resolver = SupabaseUserResolver()

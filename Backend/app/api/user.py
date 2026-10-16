from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

from app.schemas.profiles import UserProfileCreate
from app.schemas.user import UserLogin, UserSignup
from app.services.supabase_auth_service import user_resolver
from app.services.supabase_profile_service import profile_service


router = APIRouter(prefix="/users", tags=["users"])


class RefreshSessionRequest(BaseModel):
    refresh_token: str


def _session_response(auth_result: dict) -> dict:
    auth_user = auth_result.get("user") or {}
    metadata = auth_user.get("user_metadata") or {}
    return {
        "message": "로그인 성공",
        "access_token": auth_result.get("access_token"),
        "refresh_token": auth_result.get("refresh_token"),
        "expires_in": auth_result.get("expires_in"),
        "expires_at": auth_result.get("expires_at"),
        "token_type": auth_result.get("token_type", "bearer"),
        "user": {
            "id": auth_user.get("id"),
            "email": auth_user.get("email"),
            "nickname": metadata.get("nickname"),
            "name": metadata.get("name"),
            "phone_number": metadata.get("phone_number"),
        },
    }


@router.post("/signup", status_code=status.HTTP_201_CREATED)
async def signup(user: UserSignup):
    """Create the account in Supabase Auth and provision its app profile."""
    auth_result = await user_resolver.sign_up(
        email=user.email,
        password=user.password,
        metadata={
            "name": user.name,
            "nickname": user.nickname,
            "phone_number": user.phone_number,
        },
    )
    auth_user = auth_result.get("user") or {}
    user_id = auth_user.get("id")
    if not user_id:
        raise HTTPException(status_code=502, detail="Supabase Auth returned no user id.")

    # Keep application data out of auth.users. The SQL migration also installs
    # a trigger, while this upsert makes signup work during a rolling deploy.
    await profile_service.upsert_profile(
        user_id,
        UserProfileCreate(nickname=user.nickname),
    )

    return {
        "message": (
            "인증 메일을 확인해주세요."
            if not auth_result.get("access_token")
            else "회원가입이 완료되었습니다."
        ),
        "user_id": user_id,
        "requires_email_confirmation": not bool(auth_result.get("access_token")),
    }


@router.post("/login")
async def login(user: UserLogin):
    """Authenticate with Supabase and return its refreshable session."""
    auth_result = await user_resolver.sign_in_with_password(user.email, user.password)
    return _session_response(auth_result)


@router.post("/refresh")
async def refresh_session(body: RefreshSessionRequest):
    auth_result = await user_resolver.refresh_session(body.refresh_token)
    return _session_response(auth_result)

# app/core/security.py

from datetime import datetime, timedelta
import os

from dotenv import load_dotenv
from jose import JWTError, jwt
from passlib.context import CryptContext


# .env 파일 로드
load_dotenv()


# =========================================================
# 비밀번호 해싱 설정
# =========================================================

pwd_context = CryptContext(
    schemes=["pbkdf2_sha256"],
    deprecated="auto"
)


# =========================================================
# JWT 설정
# =========================================================

SECRET_KEY = os.getenv("SECRET_KEY")
ALGORITHM = os.getenv("ALGORITHM", "HS256")

ACCESS_TOKEN_EXPIRE_MINUTES = int(
    os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", 30)
)


# =========================================================
# 비밀번호 해싱 함수
# =========================================================

def hash_password(password: str) -> str:
    """
    비밀번호를 해싱해서 DB에 저장할 값으로 변환
    """

    return pwd_context.hash(password)


# =========================================================
# 비밀번호 검증 함수
# =========================================================

def verify_password(
    plain_password: str,
    hashed_password: str
) -> bool:
    """
    사용자가 입력한 비밀번호와 DB에 저장된 해시 비밀번호 비교
    """

    return pwd_context.verify(
        plain_password,
        hashed_password
    )


# =========================================================
# JWT Access Token 생성 함수
# =========================================================

def create_access_token(data: dict) -> str:
    """
    JWT Access Token 생성 함수

    JWT 안에 들어갈 데이터 예시:
    {
        "sub": "5",
        "email": "user@example.com"
    }
    """

    to_encode = data.copy()

    expire = datetime.utcnow() + timedelta(
        minutes=ACCESS_TOKEN_EXPIRE_MINUTES
    )

    to_encode.update({
        "exp": expire
    })

    encoded_jwt = jwt.encode(
        to_encode,
        SECRET_KEY,
        algorithm=ALGORITHM
    )

    return encoded_jwt


# =========================================================
# JWT Access Token 검증 함수
# =========================================================

def verify_access_token(token: str) -> dict | None:
    """
    JWT Access Token 검증 함수

    입력:
    - token: 클라이언트가 보낸 JWT 문자열

    동작:
    1. SECRET_KEY와 ALGORITHM으로 토큰 해석
    2. 토큰이 정상인지 확인
    3. 만료 시간이 지났는지 확인
    4. payload 반환

    반환:
    - 성공: payload dict
    - 실패: None
    """

    try:
        payload = jwt.decode(
            token,
            SECRET_KEY,
            algorithms=[ALGORITHM]
        )

        return payload

    except JWTError:
        return None
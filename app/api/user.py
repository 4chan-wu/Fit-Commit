# app/api/user.py

from fastapi import (
    APIRouter,
    Depends,
    HTTPException
)


from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError

from datetime import datetime, timedelta

from app.db.session import get_db
from app.models import User
from app.schemas import UserSignup, UserLogin

from app.core.security import (
    hash_password,
    verify_password,
    create_access_token,
    verify_access_token
)


# =========================================================
# JWT 토큰 인증 설정
# =========================================================

# =========================================================
# users API Router
# =========================================================

router = APIRouter(
    prefix="/users",
    tags=["users"]
)


# =========================================================
# 회원가입 API
# =========================================================

@router.post("/signup")
def signup(
    user: UserSignup,
    db: Session = Depends(get_db)
):
    """
    회원가입 API

    동작:
    1. 이메일 중복 체크
    2. 닉네임 중복 체크
    3. 전화번호 중복 체크
    4. 비밀번호 해싱
    5. DB 저장
    """

    # 이메일 중복 확인
    existing_user = db.query(User).filter(
        User.email == user.email
    ).first()

    if existing_user:
        raise HTTPException(
            status_code=400,
            detail="이미 사용 중인 이메일입니다."
        )

    # 닉네임 중복 확인
    existing_nickname = db.query(User).filter(
        User.nickname == user.nickname
    ).first()

    if existing_nickname:
        raise HTTPException(
            status_code=400,
            detail="이미 사용 중인 닉네임입니다."
        )

    # 전화번호 중복 확인
    existing_phone = db.query(User).filter(
        User.phone_number == user.phone_number
    ).first()

    if existing_phone:
        raise HTTPException(
            status_code=400,
            detail="이미 사용 중인 전화번호입니다."
        )

    # 비밀번호 해싱
    hashed_pw = hash_password(user.password)

    # 사용자 생성
    new_user = User(
        email=user.email,
        nickname=user.nickname,
        name=user.name,
        phone_number=user.phone_number,
        password=hashed_pw,
        failed_login_count=0,
        locked_until=None
    )

    try:
        db.add(new_user)
        db.commit()
        db.refresh(new_user)

    except IntegrityError as e:
        db.rollback()

        raise HTTPException(
            status_code=400,
            detail=str(getattr(e, "orig", e))
        )

    return {
        "message": "회원가입이 완료되었습니다.",
        "user_id": new_user.id
    }


# =========================================================
# 로그인 API
# =========================================================

@router.post("/login")
def login(
    user: UserLogin,
    db: Session = Depends(get_db)
):
    """
    로그인 API

    동작:
    1. 이메일 조회
    2. 계정 잠금 확인
    3. 비밀번호 검증
    4. JWT 발급
    """

    # 이메일로 사용자 조회
    db_user = db.query(User).filter(
        User.email == user.email
    ).first()

    # 사용자 없음
    if not db_user:
        raise HTTPException(
            status_code=400,
            detail="이메일 또는 비밀번호가 올바르지 않습니다."
        )

    # 계정 잠금 확인
    if (
        db_user.locked_until and
        db_user.locked_until > datetime.utcnow()
    ):
        raise HTTPException(
            status_code=403,
            detail="계정이 잠겨 있습니다. 잠시 후 다시 시도하세요."
        )

    # 비밀번호 검증 실패
    if not verify_password(
        user.password,
        db_user.password
    ):

        db_user.failed_login_count += 1

        # 5회 실패 시 3분 잠금
        if db_user.failed_login_count >= 5:
            db_user.locked_until = (
                datetime.utcnow() + timedelta(minutes=3)
            )

        db.commit()

        raise HTTPException(
            status_code=400,
            detail="이메일 또는 비밀번호가 올바르지 않습니다."
        )

    # 로그인 성공 시 초기화
    db_user.failed_login_count = 0
    db_user.locked_until = None

    db.commit()

    # JWT 생성
    access_token = create_access_token(
        data={
            "sub": str(db_user.id),
            "email": db_user.email
        }
    )

    return {
        "message": "로그인 성공",
        "access_token": access_token,
        "token_type": "bearer",
        "user": {
            "id": db_user.id,
            "email": db_user.email,
            "nickname": db_user.nickname,
            "name": db_user.name,
            "phone_number": db_user.phone_number
        }
    }

# =========================================================
# 현재 로그인한 사용자 가져오기 함수
# =========================================================

def get_current_user(
    token: str,
    db: Session
):
    """
    JWT 토큰으로 현재 로그인한 사용자 조회

    동작:
    1. JWT 검증
    2. 토큰에서 user_id 추출
    3. DB에서 사용자 조회
    4. User 객체 반환
    """

    payload = verify_access_token(token)

    if payload is None:
        raise HTTPException(
            status_code=401,
            detail="유효하지 않거나 만료된 토큰입니다."
        )

    user_id = payload.get("sub")

    if user_id is None:
        raise HTTPException(
            status_code=401,
            detail="토큰에 사용자 정보가 없습니다."
        )

    db_user = db.query(User).filter(
        User.id == int(user_id)
    ).first()

    if not db_user:
        raise HTTPException(
            status_code=404,
            detail="사용자를 찾을 수 없습니다."
        )

    return db_user

# =========================================================
# 현재 로그인한 사용자 조회 API
# =========================================================

@router.get("/me")
def get_me(
    token: str,
    db: Session = Depends(get_db)
):
    """
    현재 로그인한 사용자 조회 API

    개발 테스트용:
    - token 칸에 access_token만 입력
    - Bearer 붙이지 않음
    """

    current_user = get_current_user(
        token=token,
        db=db
    )

    return {
        "id": current_user.id,
        "email": current_user.email,
        "nickname": current_user.nickname,
        "name": current_user.name,
        "phone_number": current_user.phone_number
    }


# =========================================================
# 전체 사용자 조회 API
# =========================================================

@router.get("/")
def get_users(
    db: Session = Depends(get_db)
):
    """
    전체 사용자 조회 API
    """

    users = db.query(User).order_by(User.id).all()

    return [
        {
            "id": user.id,
            "email": user.email,
            "nickname": user.nickname,
            "name": user.name,
            "phone_number": user.phone_number,
            "failed_login_count": user.failed_login_count,
            "locked_until": user.locked_until
        }
        for user in users
    ]

# =========================================================
# 인증 테스트 API
# =========================================================

@router.get("/auth-test")
def auth_test(
    token: str,
    db: Session = Depends(get_db)
):
    """
    인증 테스트 API

    로그인한 사용자만 접근 가능
    """

    current_user = get_current_user(
        token=token,
        db=db
    )

    return {
        "message": "인증 성공",
        "user_id": current_user.id,
        "email": current_user.email
    }
    

# =========================================================
# 특정 사용자 조회 API
# =========================================================

@router.get("/{user_id}")
def get_user(
    user_id: int,
    db: Session = Depends(get_db)
):
    """
    특정 사용자 조회 API
    """

    db_user = db.query(User).filter(
        User.id == user_id
    ).first()

    if not db_user:
        raise HTTPException(
            status_code=404,
            detail="사용자를 찾을 수 없습니다."
        )

    return {
        "id": db_user.id,
        "email": db_user.email,
        "nickname": db_user.nickname,
        "name": db_user.name,
        "phone_number": db_user.phone_number
    }

    

# =========================================================
# 사용자 수정 API
# =========================================================

@router.put("/{user_id}")
def update_user(
    user_id: int,
    nickname: str | None = None,
    name: str | None = None,
    phone_number: str | None = None,
    db: Session = Depends(get_db)
):
    """
    사용자 수정 API
    """

    db_user = db.query(User).filter(
        User.id == user_id
    ).first()

    if not db_user:
        raise HTTPException(
            status_code=404,
            detail="사용자를 찾을 수 없습니다."
        )

    # 닉네임 수정
    if nickname is not None:
        db_user.nickname = nickname

    # 이름 수정
    if name is not None:
        db_user.name = name

    # 전화번호 수정
    if phone_number is not None:
        db_user.phone_number = phone_number

    try:
        db.commit()
        db.refresh(db_user)

    except IntegrityError as e:
        db.rollback()

        raise HTTPException(
            status_code=400,
            detail=str(getattr(e, "orig", e))
        )

    return {
        "message": "사용자 정보가 수정되었습니다."
    }


# =========================================================
# 사용자 삭제 API
# =========================================================

@router.delete("/{user_id}")
def delete_user(
    user_id: int,
    db: Session = Depends(get_db)
):
    """
    사용자 삭제 API
    """

    db_user = db.query(User).filter(
        User.id == user_id
    ).first()

    if not db_user:
        raise HTTPException(
            status_code=404,
            detail="사용자를 찾을 수 없습니다."
        )

    db.delete(db_user)
    db.commit()

    return {
        "message": "사용자가 삭제되었습니다.",
        "deleted_user_id": user_id
    }

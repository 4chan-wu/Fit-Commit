from sqlalchemy import Column, Integer, String, DateTime
from app.db.base import Base


class User(Base):
    __tablename__ = "users"

    # 기본 키
    id = Column(Integer, primary_key=True, index=True)

    # 이메일
    email = Column(String, unique=True, index=True, nullable=False)

    # 이름
    name = Column(String, nullable=False)
    
    # 닉네임
    nickname = Column(String, unique=True, index=True, nullable=False)

    # 전화번호
    phone_number = Column(String, unique=True, nullable=False)

    # 비밀번호
    password = Column(String, nullable=False)
    
    # 로그인 실패 횟수 (기본 0)
    failed_login_count = Column(Integer, nullable=False, default=0)

    # 계정 잠금 해제 시각 (처음엔 없음)
    locked_until = Column(DateTime, nullable=True)
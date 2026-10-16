# app/schemas.py

import re
from pydantic import BaseModel, EmailStr, field_validator


class UserSignup(BaseModel):
    # 이메일 (Pydantic이 기본적으로 형식 검증해줌)
    email: EmailStr

    # 이름
    name: str
    
    # 닉네임
    nickname: str

    # 전화번호
    phone_number: str

    # 비밀번호
    password: str

    # 비밀번호 확인
    password_confirm: str

    # -------------------------------
    # 이메일 검증
    # -------------------------------
    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str):
        # EmailStr이 이미 이메일 형식을 검증하지만,
        # 요구사항을 명확히 반영하기 위해 @ 포함 여부를 한 번 더 확인
        if "@" not in v:
            raise ValueError("이메일 형식이 아닙니다.")
        return v

    # -------------------------------
    # 이름 검증 (국제 사용자 이름과 공백 허용)
    # -------------------------------
    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str):
        normalized = v.strip()
        if not normalized or not all(char.isalpha() or char.isspace() for char in normalized):
            raise ValueError("이름은 문자와 공백만 입력 가능합니다.")
        return normalized
    
    # -------------------------------
    # 전화번호 검증
    # -------------------------------
    @field_validator("phone_number")
    @classmethod
    def validate_phone_number(cls, v: str):
        # 01012345678 / 0111234567 형태 허용
        if not re.fullmatch(r"010\d{8}|01[16789]\d{7,8}", v):
            raise ValueError("전화번호 형식이 올바르지 않습니다.")
        return v

    # -------------------------------
    # 비밀번호 검증
    # -------------------------------
    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str):
        # 영문자, 숫자, 특수문자만 허용
        if not re.fullmatch(r"[A-Za-z\d!@#$%^&*()_\-+=\[{\]};:'\",<.>/?\\|`~]+", v):
            raise ValueError("비밀번호는 영문자, 숫자, 특수문자만 입력 가능합니다.")

        # 8자리 이상인지 확인
        if len(v) < 8:
            raise ValueError("비밀번호는 8자리 이상이어야 합니다.")

        # 각 조건 포함 여부 확인
        has_upper = bool(re.search(r"[A-Z]", v))   # 대문자
        has_lower = bool(re.search(r"[a-z]", v))   # 소문자
        has_digit = bool(re.search(r"\d", v))      # 숫자
        has_special = bool(re.search(r"[!@#$%^&*()_\-+=\[{\]};:'\",<.>/?\\|`~]", v))  # 특수문자

        # 대문자/소문자/숫자/특수문자 중 몇 가지가 포함됐는지 계산
        count = sum([has_upper, has_lower, has_digit, has_special])

        # 4가지 중 3가지 이상 포함해야 통과
        if count < 3:
            raise ValueError("비밀번호는 영문 대문자, 영문 소문자, 숫자, 특수문자 중 3가지 이상 포함해야 합니다.")

        return v

    # -------------------------------
    # 비밀번호 확인 검증
    # -------------------------------
    @field_validator("password_confirm")
    @classmethod
    def validate_password_confirm(cls, v: str, info):
        # 앞에서 입력한 password 값 가져오기
        password = info.data.get("password")

        # password와 password_confirm 값이 같은지 확인
        if password is not None and v != password:
            raise ValueError("비밀번호 확인이 일치하지 않습니다.")

        return v


class UserLogin(BaseModel):
    # 로그인 시 입력받을 이메일
    email: EmailStr

    # 로그인 시 입력받을 비밀번호
    password: str

from fastapi import FastAPI
from sqlalchemy import text
import time

from app.db.session import engine
from app.db.base import Base
from app import models
from app.api import user, health, db

# FastAPI 애플리케이션 생성
app = FastAPI()


@app.on_event("startup")
def on_startup():
    """
    서버 시작 시 실행되는 초기 설정 함수

    역할:
    1. DB 연결 확인 (재시도 로직 포함)
    2. 테이블 자동 생성

    이유:
    - Docker 환경에서는 DB가 늦게 뜰 수 있음
    - 서버가 먼저 실행되면 DB 연결 에러 발생 가능
    """

    # DB 연결 재시도 (최대 10번)
    for i in range(10):
        try:
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))  # DB 연결 테스트 쿼리
            break  # 성공하면 반복 종료

        except Exception:
            print(f"DB not ready... retry {i + 1}/10")
            time.sleep(2)  # 2초 대기 후 재시도

    # 현재 등록된 테이블 출력 (디버깅용)
    print("registered tables:", Base.metadata.tables.keys())

    # SQLAlchemy 모델 기반으로 테이블 생성
    # (이미 존재하면 생성하지 않음)
    Base.metadata.create_all(bind=engine)


# =========================
# API 라우터 등록
# =========================

# 사용자 관련 API (/users)
app.include_router(user.router)

# 서버 상태 확인 API (/ , /health)
app.include_router(health.router)

# DB 상태 확인 API (/db-info, /db-check)
app.include_router(db.router)
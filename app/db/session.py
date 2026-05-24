import os

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

# .env 파일 로드 (환경변수 가져오기)
load_dotenv()

# DB 접속 정보 환경변수에서 읽기
DB_HOST = os.getenv("DB_HOST")
DB_PORT = os.getenv("DB_PORT")
DB_NAME = os.getenv("DB_NAME")
DB_USER = os.getenv("DB_USER")
DB_PASSWORD = os.getenv("DB_PASSWORD")

print("HOST:", DB_HOST)
print("USER:", DB_USER)
print("PASSWORD:", DB_PASSWORD)

# 필수 환경변수가 하나라도 없으면 에러 발생
if not all([DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD]):
    raise ValueError("Database environment variables are not set properly.")

# PostgreSQL 연결 URL 생성
# 형식: postgresql://유저:비밀번호@호스트:포트/DB이름
DATABASE_URL = (
    f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
)

# SQLAlchemy 엔진 생성 (DB 연결 담당)
engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,  # 연결이 끊어졌는지 확인 후 자동 재연결
)

# 세션 생성기 (DB 작업 단위)
SessionLocal = sessionmaker(
    autocommit=False,  # 자동 커밋 비활성화 (직접 commit 필요)
    autoflush=False,   # 자동 flush 비활성화 (성능 및 제어)
    bind=engine,
)


def get_db():
    """
    DB 세션 제공 함수 (FastAPI 의존성 주입용)

    사용 예:
    def api(db: Session = Depends(get_db)):

    동작:
    - 요청마다 DB 세션 생성
    - 작업 후 자동으로 세션 종료

    장점:
    - 메모리 누수 방지
    - DB 연결 관리 자동화
    """

    db = SessionLocal()  # 세션 생성

    try:
        yield db  # API에서 사용하도록 전달

    finally:
        db.close()  # 요청 끝나면 세션 종료
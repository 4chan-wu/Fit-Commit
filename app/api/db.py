from fastapi import APIRouter
from sqlalchemy import text

from app.db.session import engine

# 데이터베이스 관련 테스트/확인용 API 라우터
router = APIRouter(tags=["database"])


@router.get("/db-info")
def db_info():
    """
    DB 상세 정보 조회 API

    현재 연결된 데이터베이스의:
    - DB 이름
    - 스키마
    - 서버 주소
    - 서버 포트

    를 반환한다.

    주로:
    - DB 연결 상태 확인
    - Docker 내부 DB 연결 확인
    - 환경 설정 검증

    용도로 사용
    """

    # DB 엔진을 통해 연결 생성
    with engine.connect() as conn:
        # PostgreSQL 시스템 정보 조회 쿼리 실행
        result = conn.execute(
            text("SELECT current_database(), current_schema(), inet_server_addr(), inet_server_port()")
        )
        # 결과 한 줄 가져오기
        row = result.fetchone()

    # 조회한 정보를 JSON 형태로 반환
    return {
        "database": row[0],        # 현재 DB 이름
        "schema": row[1],          # 현재 스키마
        "server_addr": str(row[2]),# DB 서버 IP (Docker 내부 IP일 수 있음)
        "server_port": row[3],     # DB 포트 (보통 5432)
    }


@router.get("/db-check")
def db_check():
    """
    DB 연결 상태 확인 API

    정상 연결 시:
    - status: ok
    - DB 정보 반환

    에러 발생 시:
    - status: error
    - 에러 메시지 반환

    주로:
    - 서버 health check
    - DB 연결 문제 디버깅
    - 배포 후 상태 확인

    용도로 사용
    """

    try:
        # DB 연결 시도
        with engine.connect() as conn:
            result = conn.execute(text("SELECT current_database(), current_schema()"))
            row = result.fetchone()

        # 정상 연결 시 응답
        return {
            "status": "ok",
            "database": row[0],
            "schema": row[1],
        }

    except Exception as e:
        # DB 연결 실패 시 에러 반환
        return {
            "status": "error",
            "detail": str(e),  # 발생한 예외 메시지
        }
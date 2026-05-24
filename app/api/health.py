from fastapi import APIRouter

# 서버 상태 확인용 API 라우터
router = APIRouter(tags=["health"])


@router.get("/")
def root():
    """
    기본 루트 API

    서버가 정상적으로 실행되고 있는지 확인하기 위한 엔드포인트.

    사용 예:
    - 브라우저에서 http://localhost:8000 접속
    - 서버 실행 여부 확인

    반환:
    - 간단한 메시지
    """

    return {"message": "Fit Commit backend is running"}


@router.get("/health")
def health():
    """
    서버 헬스 체크 API

    서버 상태가 정상인지 확인하기 위한 엔드포인트.

    주로:
    - 배포 후 상태 확인
    - 로드밸런서 health check
    - 모니터링 시스템에서 사용

    반환:
    - status: ok → 서버 정상
    """

    return {"status": "ok"}
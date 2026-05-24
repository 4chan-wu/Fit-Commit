from sqlalchemy.orm import declarative_base

# SQLAlchemy에서 사용하는 기본 클래스(Base)
# 모든 모델(User 등)은 이 Base를 상속받아서 테이블로 매핑됨
Base = declarative_base()
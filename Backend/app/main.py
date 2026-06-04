import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.api.user import router as user_router
from app.api.billing import router as billing_router
from app.api.chat import router as chat_router
from app.api.clothes import router as clothes_router
from app.api.profiles import router as profiles_router
from app.api.wardrobes import router as wardrobes_router
from app.db.session import engine

app = FastAPI(title="Fit Commit Backend")

cors_origins_raw = os.getenv("CORS_ALLOW_ORIGINS", "*")
cors_allow_origins = [
    origin.strip()
    for origin in cors_origins_raw.split(",")
    if origin.strip()
]
if not cors_allow_origins:
    cors_allow_origins = ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_allow_origins,
    allow_credentials="*" not in cors_allow_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(user_router)
app.include_router(billing_router)
app.include_router(chat_router)
app.include_router(clothes_router)
app.include_router(profiles_router)
app.include_router(wardrobes_router)


@app.get("/")
def root():
    return {"message": "Fit Commit backend is running"}


@app.get("/health")
def health():
    return {"status": "ok", "service": "fitcommit-backend"}


@app.get("/db-check")
def db_check():
    try:
        with engine.connect() as conn:
            result = conn.execute(text("SELECT current_database(), current_schema()"))
            row = result.fetchone()

        return {
            "status": "ok",
            "database": row[0],
            "schema": row[1],
        }
    except Exception as e:
        return {
            "status": "error",
            "detail": str(e),
        }


@app.get("/db-info")
def db_info():
    try:
        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT current_database(), current_schema(), inet_server_addr(), inet_server_port()")
            )
            row = result.fetchone()

        return {
            "status": "ok",
            "database": row[0],
            "schema": row[1],
            "server_addr": str(row[2]),
            "server_port": row[3],
        }
    except Exception as e:
        return {
            "status": "error",
            "detail": str(e),
        }

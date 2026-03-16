from fastapi import FastAPI
from sqlalchemy import text

from app.db.session import engine

app = FastAPI()


@app.get("/")
def root():
    return {"message": "Fit Commit backend is running"}


@app.get("/health")
def health():
    return {"status": "ok"}


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
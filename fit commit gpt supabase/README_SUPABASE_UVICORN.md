# Fit Commit Backend - Supabase + Uvicorn 실행 가이드

이 버전은 Docker 구동 파일을 제거하고, 로컬에서 `uvicorn`으로 FastAPI를 실행하며 DB는 Supabase PostgreSQL에 연결하도록 정리한 버전입니다.

## 1. Supabase에 DB 생성

Supabase Dashboard → SQL Editor에서 아래 파일을 실행하세요.

```text
app/db/supabase_schema.sql
```

실행 후 Table Editor에서 `users`, `plans`, `chat_rooms`, `chat_messages` 등이 생성되었는지 확인합니다.

## 2. 환경 변수 설정

프로젝트 루트의 `.env` 파일을 열고 아래 값을 실제 값으로 교체하세요.

```env
DATABASE_URL=postgresql://postgres.xxxxx:YOUR_PASSWORD@YOUR_SUPABASE_HOST:6543/postgres?sslmode=require
OPENAI_API_KEY=your_openai_api_key_here
TOSS_CLIENT_KEY=your_toss_client_key_here
TOSS_SECRET_KEY=your_toss_secret_key_here
```

Supabase 비밀번호에 `#` 같은 특수문자가 있으면 URL 인코딩이 필요합니다.
예: `#` → `%23`

## 3. 가상환경 생성 및 실행

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## 4. 동작 확인

```text
http://localhost:8000/health
http://localhost:8000/db-check
http://localhost:8000/docs
```

`/db-check`에서 `status: ok`가 나오면 FastAPI가 Supabase DB에 연결된 상태입니다.

## 5. 참고

- Docker 관련 파일은 제거했습니다.
- Supabase DB 연결 문자열은 `.env`의 `DATABASE_URL` 하나로 관리합니다.
- `OPENAI_API_KEY`, `TOSS_*` 값은 더미이므로 실제 기능 테스트 시 직접 입력해야 합니다.

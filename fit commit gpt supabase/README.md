# Fit Commit Backend (Merged)

이 버전은 업로드된 두 백엔드 프로젝트를 하나로 합친 버전입니다.

## 포함된 기능

- 사용자 회원가입 / 로그인 / JWT 테스트 API
- 결제 테스트 API(Toss 일회성 결제 구조)
- AI 채팅 API(OpenAI 연동 구조)
- 채팅방 생성 / 조회 / 삭제
- 채팅 메시지 저장
- Docker PostgreSQL 기반 DB 자동 초기화

## 실행 방법

### 1. 환경변수 설정

프로젝트 루트의 `.env` 파일에서 아래 값을 실제 키로 교체하세요.

```env
TOSS_CLIENT_KEY=your_toss_client_key
TOSS_SECRET_KEY=your_toss_secret_key
OPENAI_API_KEY=your_openai_api_key
```

DB는 Docker PostgreSQL 기준으로 설정되어 있습니다.

```env
DB_HOST=db
DB_PORT=5432
DB_NAME=fitcommit
DB_USER=postgres
DB_PASSWORD=postgres
```

### 2. Docker 실행

처음 실행하거나 DB를 새로 만들려면 볼륨까지 삭제 후 실행하세요.

```bash
docker compose down -v
docker compose up --build -d
```

기존 데이터를 유지하면서 재실행하려면:

```bash
docker compose down
docker compose up --build -d
```

### 3. 확인

브라우저에서 확인:

```text
http://localhost:8000/health
http://localhost:8000/db-check
http://localhost:8000/docs
```

## DB 초기화

`app/db/schema.sql`이 Postgres 컨테이너의 `/docker-entrypoint-initdb.d/01-schema.sql`로 마운트되어 있습니다.

따라서 `docker compose down -v` 후 다시 실행하면 다음 데이터가 자동 생성됩니다.

- users 테스트 계정 1개
- plans 테스트 상품 2개
- 전체 서비스 테이블

## 주요 API

### 사용자
- `POST /users/signup`
- `POST /users/login`
- `GET /users/me`
- `GET /users/`

### 결제
- `GET /billing/plans`
- `POST /billing/payments/prepare`
- `POST /billing/payments/confirm`

### 채팅
- `POST /chat/rooms`
- `GET /chat/rooms`
- `GET /chat/rooms/{room_id}/messages`
- `POST /chat/rooms/{room_id}/messages`
- `DELETE /chat/rooms/{room_id}`

## 주의

- 업로드된 원본 파일 안에 있던 실제 API 키는 포함하지 않았습니다.
- `.env`의 Toss/OpenAI 키는 직접 입력해야 결제/AI 기능이 실제로 동작합니다.
- 현재 `chat.py`, `billing.py`는 테스트용으로 `user_id = 1`을 사용합니다.

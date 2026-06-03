# Fitcommit Backend

사용자 인증, DB 연동, AI 채팅, Toss Payments 결제를 담당하는 FastAPI 백엔드입니다. 팀원별로 나뉘어 있던 `Backend/juheon`, `Backend/yun` 구조를 하나의 실행 가능한 `Backend/app` 구조로 병합했습니다.

## 구조

```text
Backend/
├── app/
│   ├── api/
│   │   ├── user.py
│   │   ├── chat.py
│   │   └── billing.py
│   ├── core/
│   │   └── security.py
│   ├── db/
│   │   ├── session.py
│   │   ├── schema.sql
│   │   └── supabase_schema.sql
│   ├── schemas/
│   ├── services/
│   ├── main.py
│   └── models.py
├── Dockerfile
├── docker-compose.yml
├── requirements.txt
├── .env.example
└── README.md
```

## 주요 기능

- 사용자 회원가입/로그인/JWT 발급
- PostgreSQL 또는 Supabase 연결
- 채팅방 및 채팅 메시지 저장
- Supabase `public.clothes` 의류 저장/조회
- OpenAI API 기반 스타일 채팅 응답
- Toss Payments 결제 준비/승인
- 서버/DB 상태 확인 API

## 환경변수

`.env.example`을 `.env`로 복사한 뒤 값을 채웁니다.

```bash
cp .env.example .env
```

| 변수 | 필수 | 설명 |
| --- | --- | --- |
| `DATABASE_URL` | 필수 | PostgreSQL/Supabase 연결 문자열 |
| `SECRET_KEY` | 필수 | JWT 서명 키 |
| `ALGORITHM` | 선택 | JWT 알고리즘. 기본값 `HS256` |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | 선택 | 액세스 토큰 만료 시간 |
| `OPENAI_API_KEY` | 채팅 기능 사용 시 필수 | OpenAI API 키 |
| `SUPABASE_URL` | 의류 API 사용 시 필수 | Supabase 프로젝트 URL |
| `SUPABASE_SERVICE_ROLE_KEY` | 의류 API 사용 시 필수 | Backend 전용 service role key. iOS/Android에 넣지 않습니다 |
| `SUPABASE_ANON_KEY` | 선택 | Supabase anon key |
| `SUPABASE_CLOTHING_BUCKET` | 선택 | 의류 이미지 버킷. 기본값 `clothing-images` |
| `TOSS_CLIENT_KEY` | 결제 기능 사용 시 필수 | Toss Payments 클라이언트 키 |
| `TOSS_SECRET_KEY` | 결제 기능 사용 시 필수 | Toss Payments 시크릿 키 |
| `TOSS_SUCCESS_URL` | 선택 | 결제 성공 리다이렉트 URL |
| `TOSS_FAIL_URL` | 선택 | 결제 실패 리다이렉트 URL |

## 로컬 실행

AI 서버가 `8000`을 쓰는 경우가 많으면 Backend는 `8001`을 사용할 수 있습니다. 현재 모바일 Supabase 의류 저장 E2E 개발 기본값은 Backend `8000` 기준입니다.

```bash
cd Backend
python -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

확인:

```bash
curl http://localhost:8000/health
curl http://localhost:8000/db-check
```

## Docker 실행

```bash
cd Backend
cp .env.example .env
docker compose up --build
```

Docker Compose는 Backend를 로컬 `8001`, PostgreSQL을 로컬 `5433`에 노출합니다. PostgreSQL 컨테이너 최초 생성 시 `app/db/schema.sql`이 자동 실행됩니다.

## Supabase 사용

Supabase를 사용할 때는 `.env`의 `DATABASE_URL`을 Supabase PostgreSQL 연결 문자열로 교체하고, `app/db/supabase_schema.sql`을 Supabase SQL Editor에서 실행합니다.

의류 저장 API는 Supabase REST API를 사용합니다. `SUPABASE_SERVICE_ROLE_KEY`는 RLS를 우회할 수 있는 민감한 키이므로 Backend `.env`에만 저장하고 앱 코드에는 절대 넣지 않습니다.

의류 저장 테스트:

```bash
curl -X POST http://localhost:8000/clothes \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "00000000-0000-0000-0000-000000000000",
    "main_category": "top",
    "subcategory": "hoodie",
    "main_color": "gray",
    "season": ["fall", "winter"],
    "style_tags": ["casual", "street"],
    "image_url": "https://example.com/file.jpg",
    "image_path": "00000000-0000-0000-0000-000000000000/file.jpg",
    "confidence": 0.82,
    "analysis_method": "clip",
    "memo": ""
  }'
```

의류 목록 조회:

```bash
curl "http://localhost:8000/clothes?user_id=00000000-0000-0000-0000-000000000000"
```

## 주요 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 서버 루트 확인 |
| `GET` | `/health` | 서버 상태 확인 |
| `GET` | `/db-check` | DB 연결 확인 |
| `GET` | `/db-info` | DB 상세 정보 확인 |
| `POST` | `/users/signup` | 회원가입 |
| `POST` | `/users/login` | 로그인 |
| `GET` | `/users/me` | 현재 사용자 확인 |
| `POST` | `/clothes` | 의류 저장 |
| `GET` | `/clothes?user_id={uuid}` | 특정 사용자 의류 목록 최신순 조회 |
| `GET` | `/clothes/{clothing_id}` | 의류 상세 조회 |
| `PUT` | `/clothes/{clothing_id}` | 의류 정보 수정 |
| `DELETE` | `/clothes/{clothing_id}` | 의류 삭제 |
| `GET` | `/billing/plans` | 결제 플랜 조회 |
| `POST` | `/billing/payments/prepare` | 결제 준비 |
| `POST` | `/billing/payments/confirm` | 결제 승인 |
| `POST` | `/chat/rooms` | 채팅방 생성 |
| `GET` | `/chat/rooms` | 채팅방 목록 |
| `GET` | `/chat/rooms/{room_id}/messages` | 메시지 조회 |
| `POST` | `/chat/rooms/{room_id}/messages` | 메시지 전송 |
| `DELETE` | `/chat/rooms/{room_id}` | 채팅방 삭제 |

## 병합 기준

`Backend/juheon`은 chat/billing/OpenAI/Toss/Supabase 실행 문서가 포함되어 있어 메인 백엔드로 채택했습니다. `Backend/yun`은 user/db/health 구조가 대부분 중복이어서 앱 코드는 제거했고, Docker/PostgreSQL 실행 방식만 루트 `Dockerfile`, `docker-compose.yml`로 보존했습니다.

# Fitcommit

Fitcommit은 의류 이미지 분석, 사용자 인증, 옷장/체형 데이터 관리, AI 스타일 채팅을 연결하는 패션 AI 서비스입니다. 현재 리포지토리는 iOS 앱, AI 추론/학습 영역, Backend API 서버, 향후 웹 서비스를 위한 Frontend 자리로 정리되어 있습니다.

## 전체 아키텍처

```text
Ios(MyCloset)
  |-- 의류 이미지 업로드
  v
AI FastAPI Server
  |-- /predict
  |-- 카테고리 분류, 대표 색상 추출, 시즌 분류

Android / Ios / Future Frontend
  |-- 사용자, 채팅, 결제, DB 기반 기능 요청
  v
Backend FastAPI Server
  |-- 사용자 인증/JWT
  |-- PostgreSQL/Supabase 연동
  |-- OpenAI 기반 스타일 채팅
  |-- Toss Payments 연동
  v
PostgreSQL / Supabase
```

AI 서버와 Backend 서버는 분리되어 있습니다. AI 서버는 이미지 파일을 받아 분석 결과만 반환하는 추론 서버이고, Backend 서버는 사용자 계정, DB 저장, 채팅, 결제 등 서비스 상태를 관리합니다.

## 폴더 구조

```text
.
├── AI/
│   ├── main.py
│   ├── services/
│   ├── training/
│   ├── requirements.txt
│   └── README.md
├── Backend/
│   ├── app/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── requirements.txt
│   ├── .env.example
│   └── README.md
├── Android/
│   ├── app/
│   ├── gradle/
│   ├── build.gradle
│   └── README.md
├── Ios/
│   ├── MyCloset.xcodeproj
│   ├── MyCloset/
│   ├── MyClosetTests/
│   └── MyClosetUITests/
├── Frontend/
│   └── .gitkeep
├── README.md
└── .gitignore
```

## 서비스 역할

| 영역 | 역할 | 주요 기술 |
| --- | --- | --- |
| `AI` | 의류 이미지 분석 서버와 학습/전처리 코드 | FastAPI, PyTorch, torchvision, OpenCV, Pillow, scikit-learn |
| `Backend` | 사용자 인증, DB, 채팅, 결제 API | FastAPI, SQLAlchemy, PostgreSQL/Supabase, OpenAI API, Toss Payments |
| `Android` | Java/XML 기반 Android 앱, FastAPI 인증 API 연동 | Android SDK, Retrofit, Gson |
| `Ios` | SwiftUI 기반 MyCloset 앱 | SwiftUI, SwiftData, AVFoundation, LocalAuthentication |
| `Frontend` | 향후 웹 서비스 자리 | 예정: Node.js/Vite/React 등 |

## 실행 순서

### 1. AI 서버

```bash
cd AI
python -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

확인:

```bash
curl http://localhost:8000/health
```

### 2. Backend 서버

AI 서버가 `8000`을 사용하는 경우 Backend는 `8001`로 실행하는 것을 권장합니다.

```bash
cd Backend
cp .env.example .env
# .env 값을 로컬 Docker DB 또는 Supabase 환경에 맞게 수정
python -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

확인:

```bash
curl http://localhost:8001/health
curl http://localhost:8001/db-check
```

Docker PostgreSQL을 함께 띄우려면:

```bash
cd Backend
cp .env.example .env
docker compose up --build
```

Docker Compose는 Backend를 로컬 `8001`, PostgreSQL을 로컬 `5433`에 노출합니다.

### 3. Android 앱

Android 앱은 Retrofit으로 Backend FastAPI 서버의 인증 API를 호출합니다.

```text
Android Emulator -> http://10.0.2.2:8001 -> Backend FastAPI -> Supabase PostgreSQL
```

1. Android Studio에서 `Android/` 디렉토리를 엽니다.
2. Backend 서버를 `0.0.0.0:8001`로 실행해둡니다.
3. 에뮬레이터 브라우저에서 `http://10.0.2.2:8001/health`가 열리는지 확인합니다.
4. 앱에서 회원가입/로그인을 테스트합니다.

Android API 기준 주소는 `Android/app/src/main/java/com/fitcommit/app/services/ApiClient.java`에 있습니다.

```java
public static final String BASE_URL = "http://10.0.2.2:8001";
```

에뮬레이터에서 PC의 `localhost`에 접근할 때는 `localhost` 대신 `10.0.2.2`를 사용해야 합니다. 실제 Android 기기에서는 PC의 LAN IP를 사용합니다.

### 4. iOS 앱

1. Xcode 15 이상에서 `Ios/MyCloset.xcodeproj`를 엽니다.
2. 실제 기기에서 카메라/네트워크 기능을 테스트합니다.
3. `Ios/MyCloset/Services/AnalysisService.swift`의 AI 서버 주소를 현재 Mac의 LAN IP로 맞춥니다.
4. 앱을 빌드하고 실행합니다.

iOS 실제 기기는 `localhost`로 Mac의 서버에 접근할 수 없습니다.

### 5. Frontend 예정

`Frontend`는 현재 `.gitkeep`만 있는 빈 디렉토리입니다. 추후 웹 옷장, 결제 성공/실패 페이지, 관리자 화면, 웹 기반 AI 분석 업로드 UI를 추가할 예정입니다.

## 개발 환경 요구사항

| 영역 | 요구사항 |
| --- | --- |
| 공통 | Git, macOS 권장 |
| AI | Python 3.10 이상, pip, 선택 사항 CUDA/GPU |
| Backend | Python 3.10 이상, PostgreSQL 또는 Supabase, Docker 선택 |
| iOS | macOS, Xcode 15 이상, Swift 5.10 이상 |
| Frontend 예정 | Node.js LTS, npm 또는 pnpm |

## 환경변수

### AI

| 변수 | 필수 | 설명 | 기본값 |
| --- | --- | --- | --- |
| `CLOTHING_MODEL_PATH` | 선택 | 의류 카테고리 모델 가중치 경로 | `AI/models/clothing_mobilenet_v3_small.pth` |
| `SEASON_MODEL_PATH` | 선택 | 시즌 분류 모델 가중치 경로 | `AI/models/season_mobilenet_v3_small.pth` |

### Backend

| 변수 | 필수 | 설명 |
| --- | --- | --- |
| `DATABASE_URL` | 필수 | PostgreSQL/Supabase 연결 문자열 |
| `SECRET_KEY` | 필수 | JWT 서명 키 |
| `ALGORITHM` | 선택 | JWT 알고리즘. 기본값 `HS256` |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | 선택 | 액세스 토큰 만료 시간 |
| `OPENAI_API_KEY` | 채팅 기능 사용 시 필수 | OpenAI API 키 |
| `TOSS_CLIENT_KEY` | 결제 기능 사용 시 필수 | Toss Payments 클라이언트 키 |
| `TOSS_SECRET_KEY` | 결제 기능 사용 시 필수 | Toss Payments 시크릿 키 |
| `TOSS_SUCCESS_URL` | 선택 | 결제 성공 리다이렉트 URL |
| `TOSS_FAIL_URL` | 선택 | 결제 실패 리다이렉트 URL |

Supabase를 사용할 때는 `Backend/.env`의 `DATABASE_URL`을 Supabase PostgreSQL connection string으로 설정합니다. REST Data API URL인 `https://...supabase.co/rest/v1/`가 아니라 `postgresql://...`로 시작하는 값을 사용해야 합니다.

```env
DATABASE_URL=postgresql://postgres.xxxxx:URL_ENCODED_PASSWORD@aws-1-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require
```

비밀번호에 `@`, `#`, `%`, `/`, `:`, `?`, `&`, `+` 같은 문자가 들어가면 URL 인코딩이 필요합니다. 예를 들어 `@`는 `%40`으로 바꿉니다.

## Supabase 초기 설정 및 연결 검증

1. Supabase 프로젝트를 생성합니다.
2. Supabase Dashboard의 `SQL Editor`에서 `Backend/app/db/supabase_schema.sql` 내용을 전체 복사해 실행합니다. 파일 경로를 입력하는 것이 아니라 SQL 내용을 붙여넣어야 합니다.
3. `users.nickname` 중복 방지를 위해 아래 SQL을 추가 실행합니다.

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_nickname_unique
ON users(nickname);

CREATE INDEX IF NOT EXISTS idx_users_email
ON users(email);
```

4. Supabase의 `Connect` 또는 `Database > Connect` 화면에서 `Direct` 또는 connection string의 `URI`를 복사합니다.
5. `Backend/.env`에 `DATABASE_URL`을 설정합니다.
6. Backend 서버를 실행합니다.

```powershell
cd Backend
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

7. 새 터미널에서 연결을 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8001/health
Invoke-RestMethod http://localhost:8001/db-check
Invoke-RestMethod http://localhost:8001/db-info
```

정상 결과는 `status`가 `ok`, `database`가 `postgres`, `schema`가 `public`입니다.

회원가입 저장 테스트:

```powershell
$body = @'
{
  "email": "fitcommit_test@example.com",
  "name": "\uD64D\uAE38\uB3D9",
  "nickname": "fitcommit_test",
  "phone_number": "01012345678",
  "password": "Password1!",
  "password_confirm": "Password1!"
}
'@

Invoke-RestMethod `
  -Uri http://localhost:8001/users/signup `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $body
```

Supabase SQL Editor에서 저장 여부를 확인합니다.

```sql
SELECT id, email, name, nickname, phone_number, password, created_at
FROM users
WHERE email = 'fitcommit_test@example.com';
```

`password`가 `Password1!` 평문이 아니라 `$2b$...` 형태의 해시이면 정상입니다.

## 팀원 온보딩

1. `git status`로 현재 작업 브랜치와 변경사항을 확인합니다.
2. AI 작업자는 `AI/README.md`, Backend 작업자는 `Backend/README.md`를 먼저 읽습니다.
3. `.env.example`은 복사해서 사용하고, `.env`는 커밋하지 않습니다.
4. AI와 Backend를 동시에 띄울 때는 포트 충돌을 피합니다. 기본 권장값은 AI `8000`, Backend `8001`입니다.
5. 실제 iPhone 테스트에서는 서버 URL을 Mac의 LAN IP로 설정합니다.
6. 모델 가중치, API 키, DB 비밀번호, Xcode 사용자 상태 파일은 Git에 올리지 않습니다.

## 의류 저장 End-to-End 테스트

현재 개발 기본값:

- 테스트 Supabase Auth `user_id`: `45482f0f-42d0-401e-a18a-77abeb0492cc`
- iOS 시뮬레이터 Backend URL: `http://127.0.0.1:8000`
- Android 에뮬레이터 Backend URL: `http://10.0.2.2:8000`

테스트 순서:

1. Backend를 실행합니다.

   ```bash
   cd Backend
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```

2. iOS 시뮬레이터에서 옷을 저장합니다. 기본 개발값이 설정되어 있어 `/clothes` 저장을 먼저 시도하고, 실패하면 SwiftData 로컬 저장으로 fallback합니다.
3. Supabase Dashboard의 `public.clothes` 테이블에서 row 생성 여부를 확인합니다.
4. 앱을 재실행한 뒤 옷장 화면이 `GET /clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc` 결과를 먼저 표시하는지 확인합니다. 조회 실패 시 기존 로컬 저장소를 사용합니다.
5. Android 에뮬레이터 테스트 시 Backend URL은 `http://10.0.2.2:8000`을 사용합니다. 실제 Android 기기에서는 `Android/app/src/main/java/com/fitcommit/app/services/ApiClient.java`의 `BASE_URL`을 Mac의 LAN IP, 예: `http://192.168.0.10:8000`으로 변경합니다.

이미지 업로드 참고:

- Supabase Storage `clothing-images` bucket 업로드는 아직 구현되어 있지 않습니다.
- 현재 앱은 Supabase row에 `image_path`로 임시 참조값 또는 로컬 파일 경로를 저장합니다.
- 추후 Storage 업로드를 붙일 때는 `image_path = user_id/file.jpg`, `image_url = public or signed URL` 구조를 유지하면 됩니다.

## 주요 API

### AI

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/health` | AI 서버 상태 확인 |
| `POST` | `/predict` | 이미지 파일 업로드 후 의류 분석 |

### Backend

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/health` | 서버 상태 확인 |
| `GET` | `/db-check` | DB 연결 확인 |
| `GET` | `/db-info` | DB 서버 주소, 포트, 스키마 확인 |
| `POST` | `/users/signup` | 회원가입 |
| `POST` | `/users/login` | 로그인 |
| `GET` | `/users/me` | 현재 사용자 확인 |
| `POST` | `/clothes` | 의류 저장 |
| `GET` | `/clothes?user_id={uuid}` | 의류 목록 최신순 조회 |
| `GET` | `/clothes/{clothing_id}` | 의류 상세 조회 |
| `PUT` | `/clothes/{clothing_id}` | 의류 수정 |
| `DELETE` | `/clothes/{clothing_id}` | 의류 삭제 |
| `GET` | `/billing/plans` | 결제 플랜 조회 |
| `POST` | `/billing/payments/prepare` | 결제 준비 |
| `POST` | `/billing/payments/confirm` | 결제 승인 |
| `POST` | `/chat/rooms` | 채팅방 생성 |
| `GET` | `/chat/rooms` | 채팅방 목록 |
| `GET` | `/chat/rooms/{room_id}/messages` | 메시지 조회 |
| `POST` | `/chat/rooms/{room_id}/messages` | 메시지 전송 |
| `DELETE` | `/chat/rooms/{room_id}` | 채팅방 삭제 |

## Git에 올리면 안 되는 항목

- `.env`, 실제 API 키, DB 비밀번호, Supabase 연결 문자열
- OpenAI/Toss 실제 키
- Python 가상환경과 캐시
- Xcode `xcuserdata`, `*.xcuserstate`, DerivedData
- 빌드 결과물
- 대용량 모델 가중치
- 로컬 IDE 설정

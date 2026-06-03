# Fitcommit Backend

Fitcommit Backend는 앱에서 로컬에 먼저 저장한 옷장 메타데이터를 Supabase에 동기화하고 조회하는 FastAPI 서버임. 기존 사용자, 채팅, 결제 API도 같은 서버 안에 남아 있지만, 현재 1차 프로토타입의 핵심은 `/clothes` API와 Supabase `public.clothes` 연동임.

## 역할

- iOS/Android 앱에서 로컬 저장 후 보낸 기본 카테고리와 이미지 참조를 Supabase에 동기화함
- AI 분석 성공은 DB 저장의 필수 조건이 아님
- Backend 연결 실패는 앱의 로컬 저장을 막지 않음
- AI 성공/실패 결과를 같은 row에 업데이트함
- Supabase `public.clothes` 테이블에서 사용자별 옷 목록을 최신순으로 조회함
- 옷 단건 조회, 수정, 삭제 API를 제공함
- Supabase service role key를 Backend 내부에서만 사용함
- 앱에는 service role key나 secret key를 절대 넣지 않음

## 구조

```text
Backend/
├── app/
│   ├── api/
│   │   ├── clothes.py
│   │   ├── user.py
│   │   ├── chat.py
│   │   └── billing.py
│   ├── schemas/
│   │   └── clothes.py
│   ├── services/
│   │   └── supabase_clothes_service.py
│   ├── db/
│   ├── main.py
│   └── models.py
├── deploy/
│   └── fitcommit-backend.service.example
├── scripts/
│   └── start_backend.sh
├── requirements.txt
├── .env.example
└── README.md
```

`app/api/clothes.py`가 HTTP 라우터이고, `app/services/supabase_clothes_service.py`가 Supabase REST API 호출을 담당함.

## 환경변수

`.env.example`을 복사해서 `.env`를 만들고 값을 채움.

```bash
cd Backend
cp .env.example .env
```

Supabase 의류 API에 필요한 값은 아래와 같음.

```env
SUPABASE_URL=https://{project_ref}.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_CLOTHING_BUCKET=clothing-images
ENABLE_AI_ANALYSIS=false
CORS_ALLOW_ORIGINS=*
```

- `SUPABASE_URL`에는 Supabase Dashboard 주소가 아니라 API URL을 넣어야 함
- Dashboard 주소 예시는 `https://supabase.com/dashboard/project/{project_ref}` 형태임
- API URL 예시는 `https://{project_ref}.supabase.co` 형태임
- `SUPABASE_SERVICE_ROLE_KEY`는 RLS를 우회할 수 있는 민감한 키이므로 Backend `.env`에만 보관해야 함
- iOS/Android 코드에는 service role key를 절대 넣으면 안 됨
- `ENABLE_AI_ANALYSIS=false`는 현재 앱 테스트에서 AI 서버 없이 옷 저장만 확인하기 위한 값임
- `CORS_ALLOW_ORIGINS=*`는 개발 중에만 사용하고, 운영에서는 실제 웹 도메인으로 제한해야 함

기존 사용자/채팅/결제 라우터가 함께 로드되므로 EC2에서도 `DATABASE_URL`과 `SECRET_KEY`는 설정해야 함. 의류 API만 테스트하더라도 현재 앱 구조에서는 Backend import 단계에서 `DATABASE_URL`이 필요함.

## Supabase 테이블

Backend는 Supabase `public.clothes` 테이블을 사용함.

- `user_id`는 `auth.users(id)`를 참조하는 `uuid` 타입임
- RLS 정책은 로그인한 사용자가 자기 옷만 접근하도록 설정함
- Backend는 service role key로 서버 측 저장/조회 요청을 수행함
- Storage bucket 이름은 `clothing-images` 사용함
- Storage 업로드는 아직 미구현 상태임
- 현재는 로컬 사진 저장 + DB 메타데이터 동기화 구조임
- 다음 단계에서 `clothing-images` bucket에 이미지를 업로드해야 함
- 이후 업로드된 이미지 기반 AI 재분석과 벡터화를 연결할 예정임
- 향후 이미지 경로는 `{user_id}/{main_category}/{filename}` 형태로 구성할 예정임
- `analysis_status`는 `pending`, `completed`, `failed` 중 하나를 사용함
- `analysis_error`, `analyzed_at`, `needs_analysis`로 분석 실패/재분석 상태를 관리함
- `sync_status`는 `local_only`, `pending_sync`, `synced`, `sync_failed` 중 하나를 사용함
- `local_image_path`는 Storage 업로드 전까지 앱이 가진 로컬 이미지 참조를 기록하는 임시 필드임

기존 Supabase 테이블에는 `app/db/clothes_analysis_status_migration.sql`을 SQL Editor에서 실행해 분석/동기화 상태 컬럼을 추가함.

## 실행 방법

Python 3.11 사용을 권장함.

로컬 개발 실행은 reload를 켜고 실행함.

```bash
cd Backend
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

배포 실행은 reload 없이 실행함.

```bash
cd Backend
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

추가한 스크립트로도 실행할 수 있음.

```bash
cd Backend
./scripts/start_backend.sh
```

상태 확인은 아래처럼 함.

```bash
curl http://127.0.0.1:8000/health
```

## AWS EC2 배포

Ubuntu EC2 인스턴스를 기준으로 배포함.

1. AWS EC2에서 Ubuntu 인스턴스를 생성함
2. 보안 그룹 inbound rule에 `22/tcp`, `8000/tcp`를 허용함
3. SSH로 접속함

```bash
ssh -i your-key.pem ubuntu@EC2_PUBLIC_IP
```

4. 서버 패키지와 Python venv 도구를 설치함

```bash
sudo apt update
sudo apt install -y git python3.11 python3.11-venv python3-pip
```

5. GitHub 저장소를 clone함

```bash
git clone https://github.com/YOUR_ORG_OR_USER/Fitcommit_Development.git
cd Fitcommit_Development/Backend
```

6. 가상환경을 만들고 패키지를 설치함

```bash
python3.11 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
```

7. `.env`를 작성함

```bash
cp .env.example .env
nano .env
```

`.env`에는 실제 값을 넣되 GitHub에는 올리지 않음. `SUPABASE_SERVICE_ROLE_KEY`는 Backend 전용 secret이므로 iOS/Android에 넣으면 안 됨.

```env
DATABASE_URL=postgresql://...
SUPABASE_URL=https://{project_ref}.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_CLOTHING_BUCKET=clothing-images
ENABLE_AI_ANALYSIS=false
CORS_ALLOW_ORIGINS=*
SECRET_KEY=change-this-secret-key
```

8. 배포 명령으로 실행함

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

또는 스크립트를 사용함.

```bash
./scripts/start_backend.sh
```

9. EC2 public IP로 상태와 의류 API를 테스트함

```bash
curl http://EC2_PUBLIC_IP:8000/health
curl "http://EC2_PUBLIC_IP:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

10. iOS/Android 앱의 Backend base URL을 바꿈

```text
iOS 시뮬레이터: http://127.0.0.1:8000
Android 에뮬레이터: http://10.0.2.2:8000
팀원 실제 기기: http://EC2_PUBLIC_IP:8000
```

운영에서는 `8000`을 직접 열어두기보다 Nginx reverse proxy와 HTTPS를 붙이는 방향으로 개선해야 함.

## systemd 서비스 예시

터미널을 닫아도 Backend를 계속 실행하려면 `deploy/fitcommit-backend.service.example`을 참고함.

```bash
sudo cp deploy/fitcommit-backend.service.example /etc/systemd/system/fitcommit-backend.service
sudo nano /etc/systemd/system/fitcommit-backend.service
sudo systemctl daemon-reload
sudo systemctl enable fitcommit-backend
sudo systemctl start fitcommit-backend
sudo systemctl status fitcommit-backend
```

로그 확인은 아래처럼 함.

```bash
journalctl -u fitcommit-backend -f
```

`WorkingDirectory`, `EnvironmentFile`, `ExecStart` 경로는 EC2에 clone한 실제 경로에 맞춰 수정해야 함.

## `/clothes` API

### `POST /clothes`

앱이 로컬 저장에 성공한 뒤 서버 연결이 가능할 때 Supabase에 메타데이터를 동기화함. AI 결과와 Storage 이미지 없이도 row를 만들 수 있음.

```bash
curl -X POST http://127.0.0.1:8000/clothes \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
    "main_category": "top",
    "local_image_path": "file:///local/clothing/top-sample.jpg",
    "analysis_status": "pending",
    "needs_analysis": true,
    "sync_status": "pending_sync",
    "memo": ""
  }'
```

### `GET /clothes?user_id={uuid}`

특정 사용자의 옷 목록을 최신순으로 조회함.

```bash
curl "http://127.0.0.1:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

### `GET /clothes/{clothing_id}`

옷 단건을 조회함.

```bash
curl "http://127.0.0.1:8000/clothes/{clothing_id}?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

### `PUT /clothes/{clothing_id}`

옷 정보를 수정함.

```bash
curl -X PUT "http://127.0.0.1:8000/clothes/{clothing_id}?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc" \
  -H "Content-Type: application/json" \
  -d '{
    "subcategory": "hoodie",
    "main_color": "gray",
    "season": ["fall", "winter"],
    "style_tags": ["casual", "street"],
    "confidence": 0.82,
    "analysis_method": "clip",
    "analysis_status": "completed",
    "analysis_error": "",
    "analyzed_at": "2026-06-03T12:00:00Z",
    "needs_analysis": false,
    "sync_status": "synced"
  }'
```

AI 실패 상태 업데이트는 아래처럼 함.

```bash
curl -X PUT "http://127.0.0.1:8000/clothes/{clothing_id}" \
  -H "Content-Type: application/json" \
  -d '{
    "analysis_status": "failed",
    "analysis_error": "AI server timeout",
    "needs_analysis": true,
    "sync_status": "synced"
  }'
```

### `POST /clothes/{clothing_id}/analyze`

재분석 예약용 API임. 현재는 row 조회 후 501을 반환함. Supabase Storage에서 이미지를 내려받아 AI 서버 `/predict`로 재전송하는 구조는 TODO 상태임.

### `DELETE /clothes/{clothing_id}`

옷 정보를 삭제함.

```bash
curl -X DELETE "http://127.0.0.1:8000/clothes/{clothing_id}?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

## 앱 연동 기준

- iOS 시뮬레이터는 Backend 주소로 `http://127.0.0.1:8000` 사용 가능함
- Android 에뮬레이터는 Backend 주소로 `http://10.0.2.2:8000` 사용해야 함
- 실제 iPhone/Android 기기는 Mac과 같은 Wi-Fi에 연결한 뒤 `http://{Mac의_로컬_IP}:8000` 사용해야 함
- 앱은 로컬 저장을 먼저 완료한 뒤 Backend 동기화를 시도함
- Backend 저장 실패 시에도 로컬 item은 유지되고 `sync_status=sync_failed` 상태로 표시됨
- Supabase 저장 테스트 단계에서는 AI 서버를 실행하지 않아도 됨
- 앱의 `ENABLE_AI_ANALYSIS=false` 설정이면 `/predict` 호출 없이 `POST /clothes`만 확인함

## Supabase 저장 테스트 순서

1. Backend 서버를 실행함
2. AI 서버는 실행하지 않아도 됨
3. iOS 시뮬레이터 또는 Android 에뮬레이터에서 옷 추가를 진행함
4. Backend 터미널에 `POST /clothes` 200 OK가 찍히는지 확인함
5. Supabase `public.clothes` 테이블에 `analysis_status=pending`, `needs_analysis=true` row가 생성되는지 확인함
6. AI 분석은 나중에 앱의 `ENABLE_AI_ANALYSIS=true` 설정과 AI 서버 실행 후 별도 검증함

## 검증 상태

- Backend ↔ Supabase `POST /clothes` curl 테스트 성공함
- Supabase `public.clothes` row 저장 성공함
- Backend Python 문법 검사 성공함
- iOS 저장/조회 흐름은 SwiftData 로컬 우선, Backend 동기화 후처리 구조로 연결됨
- Android 저장/조회 흐름은 `ClothingStorage` 로컬 우선, Backend 동기화 후처리 구조로 연결됨
- `/clothes`는 AI 결과 없이도 `pending` row를 저장할 수 있게 구성됨
- `/clothes`는 `image_url`/`image_path` 없이 `local_image_path`만 있어도 row를 저장할 수 있게 구성됨

## 남은 작업

- Supabase Storage `clothing-images` 업로드 API를 연결해야 함
- `POST /clothes/{clothing_id}/analyze`에서 이미지 재전송과 AI 결과 업데이트를 완성해야 함
- 임시 `user_id` 쿼리/바디 방식에서 Supabase Auth access token 기반 인증으로 개선해야 함
- 운영 배포 환경에서는 service role key 관리와 CORS 정책을 다시 점검해야 함

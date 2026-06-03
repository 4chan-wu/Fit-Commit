# Fitcommit Backend

Fitcommit Backend는 앱에서 로컬에 먼저 저장한 옷장 메타데이터를 Supabase에 동기화하고 조회하는 FastAPI 서버임. 현재 Backend는 AWS EC2에서 `http://13.125.252.131:8000` 주소로 실행 중이며, 외부 Mac 터미널에서 Supabase `public.clothes` GET/POST 200 OK 확인이 완료됨.

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
│   ├── fitcommit-backend.service.example
│   └── restart_backend.sh
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
- Storage 업로드는 Backend `POST /clothes/upload`가 담당함
- iOS/Android 앱에는 service role key를 넣지 않고 Backend에 multipart 파일만 보냄
- 업로드 성공 시 `image_path`와 `image_url`을 `/clothes` payload에 포함함
- 업로드 실패 시에도 로컬 사진 저장 + DB 메타데이터 동기화 fallback을 사용함
- 이후 업로드된 이미지 기반 AI 재분석과 벡터화를 연결할 예정임
- Storage 이미지 경로는 `{user_id}/{main_category}/{filename}.jpg` 형태로 구성함
- `analysis_status`는 `pending`, `completed`, `failed` 중 하나를 사용함
- `analysis_error`, `analyzed_at`, `needs_analysis`로 분석 실패/재분석 상태를 관리함
- `sync_status`는 `local_only`, `pending_sync`, `synced`, `sync_failed` 중 하나를 사용함
- `local_image_path`는 앱이 가진 로컬 이미지 참조를 기록하는 fallback 필드임

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

현재 배포 주소는 아래와 같음.

```text
http://13.125.252.131:8000
```

1. AWS EC2에서 Ubuntu 인스턴스를 생성함
2. 보안 그룹 inbound rule에 `22/tcp`, `8000/tcp`를 허용함
3. SSH로 접속함

```bash
ssh -i your-key.pem ubuntu@EC2_PUBLIC_IP
```

현재 서버 접속 예시는 아래와 같음.

```bash
ssh -i your-key.pem ubuntu@13.125.252.131
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
curl http://13.125.252.131:8000/health
curl "http://13.125.252.131:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

10. iOS/Android 앱의 Backend base URL을 바꿈

```text
iOS 시뮬레이터: http://127.0.0.1:8000
Android 에뮬레이터: http://10.0.2.2:8000
팀원 실제 기기: http://13.125.252.131:8000
```

운영에서는 `8000`을 직접 열어두기보다 Nginx reverse proxy와 HTTPS를 붙이는 방향으로 개선해야 함.

## 로컬 Mac에서 EC2로 빠른 배포

프로젝트 루트에서 아래 스크립트를 실행하면 Backend 폴더만 압축해서 EC2로 전송함.

```bash
./scripts/deploy_backend_to_ec2.sh
```

기본 설정은 아래 값을 사용함.

```text
EC2_USER=ubuntu
EC2_HOST=13.125.252.131
EC2_KEY_PATH=~/Downloads/fitcommit-key.pem
REMOTE_DIR=~
ARCHIVE_NAME=backend.tar.gz
```

키 파일 위치가 다르면 아래처럼 실행함.

```bash
EC2_KEY_PATH=/path/to/your-key.pem ./scripts/deploy_backend_to_ec2.sh
```

압축 파일에는 `Backend/.env`, `Backend/.venv`, `.git`, macOS `._*` 파일이 포함되지 않음. 실제 Supabase secret key는 로컬 `.env`와 EC2 `.env`에만 있어야 함.

배포 스크립트는 `backend.tar.gz`와 함께 `restart_backend.sh`를 EC2 홈 디렉터리에 전송함. EC2에 접속한 뒤 아래 명령으로 새 Backend를 반영함.

```bash
ssh -i ~/Downloads/fitcommit-key.pem ubuntu@13.125.252.131
bash ~/restart_backend.sh
```

`restart_backend.sh`는 아래 순서로 동작함.

1. 기존 `~/Backend`를 `~/Backend.backup.YYYYMMDDHHMMSS`로 백업함
2. `~/backend.tar.gz`를 `~`에 압축 해제함
3. 백업 폴더의 `.env`를 새 `~/Backend/.env`로 복원함
4. `.venv`가 없으면 생성함
5. `pip install -r requirements.txt`를 실행함
6. tmux `fitcommit` 세션을 재시작하고 uvicorn을 실행함

실행 명령은 아래와 같음.

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

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

tmux로 간단히 유지하려면 아래처럼 실행함.

```bash
tmux new -s fitcommit-backend
cd Fitcommit_Development/Backend
source .venv/bin/activate
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

세션에서 빠져나올 때는 `Ctrl-b` 다음 `d`를 누름. 다시 접속할 때는 아래 명령을 사용함.

```bash
tmux attach -t fitcommit-backend
```

## `/clothes` API

### `POST /clothes`

앱이 로컬 저장과 Storage 업로드 시도 이후 Supabase에 메타데이터를 동기화함. AI 결과 없이도 row를 만들 수 있음. Storage 업로드가 실패하면 `local_image_path`만으로도 row를 만들 수 있음.

```bash
curl -X POST http://127.0.0.1:8000/clothes \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
    "main_category": "top",
    "local_image_path": "file:///local/clothing/top-sample.jpg",
    "analysis_status": "pending",
    "needs_analysis": true,
    "sync_status": "synced",
    "memo": ""
  }'
```

### `POST /clothes/upload`

옷 사진을 Supabase Storage `clothing-images` bucket에 업로드함. Backend가 service role key로 업로드하므로 iOS/Android에는 Supabase secret key를 넣지 않음.

```bash
curl -X POST http://13.125.252.131:8000/clothes/upload \
  -F "file=@./sample-clothing.jpg" \
  -F "user_id=45482f0f-42d0-401e-a18a-77abeb0492cc" \
  -F "main_category=top"
```

응답 예시는 아래와 같음.

```json
{
  "image_path": "45482f0f-42d0-401e-a18a-77abeb0492cc/top/20260603120000-uuid.jpg",
  "image_url": "https://{project_ref}.supabase.co/storage/v1/object/public/clothing-images/...",
  "bucket": "clothing-images"
}
```

업로드 후 Supabase Dashboard의 Storage `clothing-images` bucket에서 파일이 보이는지 확인함. 이후 `/clothes` 생성 요청에는 응답의 `image_path`와 `image_url`을 넣음.

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

- 팀원 실제 기기 테스트는 Backend 주소로 `http://13.125.252.131:8000` 사용함
- iOS 시뮬레이터 로컬 테스트는 Backend 주소로 `http://127.0.0.1:8000` 사용 가능함
- Android 에뮬레이터 로컬 테스트는 Backend 주소로 `http://10.0.2.2:8000` 사용해야 함
- 실제 iPhone/Android 로컬 Mac 테스트는 `http://{Mac의_로컬_IP}:8000` 사용해야 함
- 앱은 로컬 저장을 먼저 완료한 뒤 Backend 동기화를 시도함
- Backend 저장 실패 시에도 로컬 item은 유지되고 `sync_status=sync_failed` 상태로 표시됨
- Supabase 저장 테스트 단계에서는 AI 서버를 실행하지 않아도 됨
- 앱의 `ENABLE_AI_ANALYSIS=false` 설정이면 `/predict` 호출 없이 `POST /clothes`만 확인함
- AI 서버는 아직 배포하지 않았고, 현재는 AI 분석 비활성화 상태로 저장 테스트를 진행함

## Supabase 저장 테스트 순서

1. EC2 Backend `http://13.125.252.131:8000`을 사용함
2. AI 서버는 실행하지 않아도 됨
3. iOS 시뮬레이터 또는 Android 에뮬레이터에서 옷 추가를 진행함
4. EC2 Backend에 `POST /clothes` 요청이 성공하는지 확인함
5. Supabase `public.clothes` 테이블에 `analysis_status=pending`, `needs_analysis=true` row가 생성되는지 확인함
6. AI 분석은 나중에 앱의 `ENABLE_AI_ANALYSIS=true` 설정과 AI 서버 실행 후 별도 검증함

## 배포 후 확인 명령

GET 테스트는 아래처럼 함.

```bash
curl "http://13.125.252.131:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

AI 없이 pending row를 생성하는 POST 테스트는 아래처럼 함. 응답에서 `sync_status=synced`, `analysis_status=pending`인지 확인함.

```bash
curl -X POST http://13.125.252.131:8000/clothes \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
    "main_category": "top",
    "local_image_path": "file:///local/clothing/top-sample.jpg",
    "analysis_status": "pending",
    "needs_analysis": true,
    "sync_status": "synced",
    "memo": "ec2_deploy_test"
  }'
```

Storage 업로드 테스트는 아래처럼 함. 응답에서 `image_path`가 `local://`가 아니라 `45482f0f-.../top/...jpg` 형태인지 확인함.

```bash
curl -X POST http://13.125.252.131:8000/clothes/upload \
  -F "file=@./sample-clothing.jpg" \
  -F "user_id=45482f0f-42d0-401e-a18a-77abeb0492cc" \
  -F "main_category=top"
```

업로드 성공 후 Supabase Dashboard의 Storage `clothing-images` bucket에서 파일이 보이는지 확인함. 이후 `/clothes` row에는 Storage 응답의 `image_path`와 `image_url`을 저장해야 함.

## 검증 상태

- EC2 Backend 배포 완료함
- EC2 Backend ↔ Supabase `GET /clothes`, `POST /clothes` 외부 curl 테스트 성공함
- Supabase `public.clothes` row 저장/조회 성공함
- AI 없이 `analysis_status=pending`, `needs_analysis=true`, `sync_status=synced`로 저장하는 구조 완료함
- Supabase Storage `clothing-images` 업로드 API 추가 완료함
- Backend Python 문법 검사 성공함
- iOS 저장/조회 흐름은 SwiftData 로컬 우선, Backend 동기화 후처리 구조로 연결됨
- Android 저장/조회 흐름은 `ClothingStorage` 로컬 우선, Backend 동기화 후처리 구조로 연결됨
- `/clothes`는 AI 결과 없이도 `pending` row를 저장할 수 있게 구성됨
- `/clothes`는 `image_url`/`image_path` 없이 `local_image_path`만 있어도 row를 저장할 수 있게 구성됨
- `/clothes/upload`는 Supabase Storage `clothing-images` 업로드 후 `image_path`와 `image_url`을 반환하도록 구성됨

## 남은 작업

- 실제 AI 서버를 배포해야 함
- CLIP/FashionCLIP 분석을 운영 서버와 앱 후처리 흐름에 실제 연결하고 성능 검증해야 함
- `POST /clothes/{clothing_id}/analyze`에서 이미지 재전송과 AI 결과 업데이트를 완성해야 함
- 업로드된 Storage 이미지를 기반으로 AI 재분석과 벡터화를 연결해야 함
- 임시 `user_id` 쿼리/바디 방식에서 Supabase Auth access token 기반 인증으로 개선해야 함
- 운영 배포 환경에서는 service role key 관리와 CORS 정책을 다시 점검해야 함

# Fitcommit

Fitcommit은 사용자가 가진 옷을 등록하고, AI가 옷 정보를 분석한 뒤, 사용자의 옷장 데이터를 기반으로 코디 추천을 제공하는 앱임.

1차 프로토타입에서는 전신 사진 분석과 YOLO 기반 상하의 자동 분리를 제외함. 현재 구조는 사용자가 상의, 하의, 아우터, 신발, 모자, 가방, 원피스 같은 큰 카테고리를 먼저 고르고, 아이템별 사진을 따로 촬영하거나 업로드하는 방식으로 구성됨.

## 현재 아키텍처

```text
iOS 앱 / Android 앱
  ├─ 카테고리 선택
  ├─ 아이템 단독 사진 촬영 또는 업로드
  ├─ 로컬 옷장에 먼저 저장
  │    └─ sync_status=pending_sync 또는 local_only, analysis_status=pending
  ├─ Backend POST /clothes 동기화 시도
  │    ├─ Backend POST /clothes/upload로 Storage 업로드 시도
  │    ├─ 업로드 성공 시 image_path/image_url 저장
  │    ├─ 업로드 실패 시 local:// fallback 저장
  │    ├─ 성공 시 remote_id 저장 + sync_status=synced
  │    └─ 실패 시 로컬 유지 + sync_status=sync_failed
  ├─ AI 서버 POST /predict 후처리 시도
  │    └─ multipart/form-data: file + category_hint
  └─ Backend PUT /clothes/{clothing_id} 후처리
       ├─ AI 성공 시 completed + 분석 결과 업데이트
       └─ AI 실패 시 failed + analysis_error 저장

Python FastAPI AI 서버
  └─ /predict
       ├─ CLIP 이미지-텍스트 similarity 기반 세부 카테고리 추론
       ├─ MobileNetV3 fallback
       ├─ 대표 색상 추출
       └─ 시즌/스타일 태그 반환

Python FastAPI Backend 서버
  ├─ AWS EC2: http://54.180.79.90:8000
  └─ /clothes CRUD
       └─ Supabase public.clothes 저장/조회/수정/삭제

Supabase
  ├─ PostgreSQL public.clothes
  └─ Storage clothing-images bucket
```

AI 서버와 Backend 서버는 분리되어 있음. Backend는 현재 AWS EC2에서 `http://54.180.79.90:8000` 주소로 실행 중임. 앱은 사진과 기본 카테고리를 로컬에 먼저 저장하고, EC2 Backend를 통해 Supabase에 메타데이터를 동기화함. AI 서버는 아직 배포하지 않았고, 현재 팀원 테스트는 AI 분석 비활성화 상태로 저장/조회만 확인함.

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
│   ├── deploy/
│   │   ├── fitcommit-backend.service.example
│   │   └── restart_backend.sh
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
│   ├── MyClosetUITests/
│   └── README.md
├── Frontend/
│   └── .gitkeep
├── scripts/
│   └── deploy_backend_to_ec2.sh
└── README.md
```

`Frontend/`는 현재 자리만 있는 상태임. 웹 서비스는 아직 구현되지 않음.

## 현재 주요 기능

- 사용자가 업로드 전에 큰 카테고리를 선택함
- 허용 카테고리는 `top`, `bottom`, `outer`, `shoes`, `hat`, `bag`, `dress` 사용함
- 촬영/업로드 화면은 전신 사진이 아니라 옷 한 벌만 중앙에 두고 찍도록 안내함
- AI 분석 성공은 저장의 필수 조건이 아님
- 서버/Supabase 저장 성공도 로컬 저장의 필수 조건이 아님
- 앱은 AI 분석과 서버 동기화 전에 SwiftData 또는 `ClothingStorage`에 먼저 저장함
- 최초 로컬 데이터는 `sync_status=pending_sync`, `analysis_status=pending`, `needs_analysis=true` 상태임
- AI 서버가 없어도 사용자는 카테고리, 세부 종류, 대표 색상, 계절을 직접 수정할 수 있음
- 직접 수정한 값은 Backend `PUT /clothes/{clothing_id}`로 Supabase에 반영해야 함
- 수동 수정은 `analysis_method=manual`을 사용하고, AI 분석 완료를 의미하지 않으므로 `analysis_status=pending`을 유지할 수 있음
- 대표색 추출은 iOS/Android 로컬 이미지 처리 기반 추천값이며 사용자가 직접 바꿀 수 있음
- 배경 제거는 저장 필수 조건이 아니며 원본 사진 저장을 우선하고 향후 선택 기능으로 분리함
- Backend 연결이 가능하면 `/clothes`로 DB 메타데이터 동기화를 시도함
- Backend 연결이 가능하면 `/clothes/upload`로 사진을 `clothing-images` bucket에 업로드함
- Storage 업로드 성공 시 `/clothes` payload에 Supabase Storage `image_path`와 `image_url`을 포함함
- Storage 업로드 실패 시에도 등록은 계속 진행하고 `local://` 또는 앱 내부 경로를 fallback으로 저장함
- Backend 저장 성공 시 로컬 데이터에 `remote_id`를 저장하고 `sync_status=synced`로 업데이트함
- Backend 저장 실패 시 로컬 데이터는 유지하고 `sync_status=sync_failed`, `sync_error`를 저장함
- `ENABLE_AI_ANALYSIS=false`이면 앱은 AI 서버 `/predict`를 호출하지 않고 `analysis_status=pending` 상태로 저장만 함
- `ENABLE_AI_ANALYSIS=true`이면 저장 후 AI 서버 `/predict`로 `file`과 `category_hint`를 multipart로 전송함
- AI 성공 시 앱은 Backend `PUT /clothes/{clothing_id}`로 `analysis_status=completed`, `needs_analysis=false`, `analyzed_at`과 분석 결과를 업데이트함
- AI 실패 시 앱은 같은 row를 유지하고 `analysis_status=failed`, `needs_analysis=true`, `analysis_error`를 저장함
- AI 실패 또는 서버 실패가 발생해도 옷장 화면에는 로컬 아이템이 남아 있어야 함
- 앱에서 AI 분석 결과를 확인/수정한 뒤 사용자가 확정한 값으로 로컬과 서버 row를 다시 업데이트함
- Supabase Storage 이미지 업로드는 Backend `/clothes/upload`로 연결됨
- Supabase Dashboard에서 사진을 보려면 `clothing-images` bucket에 실제 파일이 올라가야 함
- `local://` 경로는 앱 내부 참조라 Supabase Dashboard에서 볼 수 없음
- Backend는 AWS EC2에서 실행 중이며 외부 Mac 터미널 기준 `GET /clothes`, `POST /clothes` 200 OK 확인 완료함

## 현재 AI 구조

`AI/services/fashion_clip_service.py`가 의류 세부 카테고리 분석의 중심 서비스임.

- 현재 코드 기준으로 transformers 기반 CLIP 추론이 연결되어 있음
- 기본 모델은 `openai/clip-vit-base-patch32` 사용함
- `FASHION_CLIP_MODEL_ID` 환경변수로 Hugging Face FashionCLIP 계열 모델로 교체할 수 있게 구성됨
- 모델은 매 요청마다 로딩하지 않고 lazy singleton 구조로 1회 로딩함
- GPU가 있으면 `cuda`, 없으면 `cpu`로 동작함
- CLIP 모델 로딩 또는 추론 실패 시 기존 MobileNetV3 기반 fallback을 사용함
- 실제 CLIP 사용 시 `method`는 `"clip"`으로 반환함
- fallback 사용 시 `method`는 `"fallback"`으로 반환함

`/predict`는 아래 `category_hint` 값만 허용함.

```text
top
bottom
outer
shoes
hat
bag
dress
```

## Supabase 설정

Fitcommit은 Supabase의 `public.clothes` 테이블을 옷장 저장소로 사용함.

- `user_id`는 `auth.users(id)`를 참조하는 `uuid` 타입임
- 사용자 프로필은 `public.user_profiles`를 사용하며 `user_id uuid primary key references auth.users(id) on delete cascade` 구조로 연결함
- 사용자별 옷장은 `public.wardrobes`를 사용하며 `wardrobes.user_id`가 `auth.users(id)`를 참조함
- 사용자는 여러 개의 옷장을 가질 수 있음
- 기본 옷장은 사용자당 1개만 가능함
- 추가 옷장은 여러 개 만들 수 있음
- 같은 사용자 안에서는 같은 옷장 이름을 중복해서 만들 수 없도록 unique index를 사용함
- `clothes.wardrobe_id`는 있으면 특정 옷장에 연결하고, 없으면 기존처럼 `clothes.user_id` 기준으로 조회함
- 옷 등록 시 `wardrobe_id`가 없으면 Backend가 기본 옷장을 자동 생성 또는 조회해서 `clothes.wardrobe_id`에 연결함
- iOS, Android, 추후 웹은 모두 같은 Backend wardrobe API를 사용해야 함
- RLS 정책은 로그인한 사용자가 자기 옷만 접근할 수 있도록 설정함
- Backend는 서버 내부에서만 `SUPABASE_SERVICE_ROLE_KEY`를 사용함
- 현재 Backend는 service role key로 Supabase REST API를 호출하므로 RLS를 우회할 수 있음
- 실제 서비스 전에는 앱이 Supabase Auth access token을 보내고 Backend가 `Authorization: Bearer <token>`에서 user_id를 결정하도록 전환해야 함
- 개발 중에는 `X-Test-User-Id` 또는 기존 테스트 user_id를 fallback으로 사용할 수 있음
- service role key나 secret key는 iOS/Android 코드에 절대 넣으면 안 됨
- Storage bucket 이름은 `clothing-images` 사용함
- Storage 업로드는 Backend `POST /clothes/upload`가 담당함
- 앱은 service role key 없이 Backend에 multipart 파일만 보냄
- 업로드 성공 시 `image_path`는 `{user_id}/{main_category}/{filename}.jpg` 형태로 저장함
- 업로드 실패 시에는 로컬 사진 저장 + DB 메타데이터 동기화 fallback을 사용함
- 이후 업로드된 이미지 기반 AI 재분석과 벡터화를 연결할 예정임
- 분석/동기화 상태 관리를 위해 `analysis_status`, `analysis_error`, `analyzed_at`, `needs_analysis`, `sync_status`, `local_image_path` 컬럼을 사용함
- 기존 Supabase 테이블에는 `Backend/app/db/clothes_analysis_status_migration.sql`을 SQL Editor에서 실행해 의류 상태 컬럼을 추가함
- 사용자별 프로필/옷장 구조는 `Backend/app/db/user_profiles_wardrobes_migration.sql`을 SQL Editor에서 실행해 추가함
- 기본 옷장 제약과 중복 이름 방지는 `Backend/app/db/wardrobes_default_constraints_migration.sql`을 SQL Editor에서 실행해 적용함

Supabase Dashboard 주소와 API URL은 다름.

```text
Dashboard 주소 예시: https://supabase.com/dashboard/project/{project_ref}
Backend .env URL 예시: https://{project_ref}.supabase.co
```

Backend `.env`의 `SUPABASE_URL`에는 Dashboard 주소가 아니라 `https://{project_ref}.supabase.co` 형태의 API URL을 넣어야 함.

## Backend 실행 방법

Python 3.11 사용을 권장함.

```bash
cd Backend
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Backend 상태 확인은 아래처럼 함.

```bash
curl http://127.0.0.1:8000/health
```

배포 실행은 reload 없이 실행함.

```bash
cd Backend
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

추가된 스크립트로도 실행 가능함.

```bash
cd Backend
./scripts/start_backend.sh
```

Backend `.env` 예시는 아래처럼 작성함. 실제 키 값은 README에 적지 않아야 함.

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

`SUPABASE_SERVICE_ROLE_KEY`는 Backend 전용 키임. iOS/Android 앱, GitHub, 공개 문서에 넣으면 안 됨.
`CORS_ALLOW_ORIGINS=*`는 개발 중에만 사용하고, 운영에서는 실제 웹 도메인으로 제한해야 함.

## AWS EC2 Backend 배포 요약

현재 Backend는 AWS EC2에서 실행 중임.

```text
http://54.180.79.90:8000
```

외부 Mac 터미널에서 Supabase DB 연동 `GET /clothes`, `POST /clothes` 200 OK 확인 완료함. 자세한 절차는 `Backend/README.md`의 `AWS EC2 배포` 섹션을 기준으로 함.

1. Ubuntu EC2 인스턴스를 생성함
2. 보안 그룹 inbound rule에 `22/tcp`, `8000/tcp`를 허용함
3. EC2에 SSH 접속 후 GitHub 저장소를 clone함
4. `Backend/`에서 Python 3.11 venv를 생성함
5. `pip install -r requirements.txt`를 실행함
6. `.env.example`을 복사해 `.env`를 작성함
7. `python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`으로 실행함
8. `curl http://54.180.79.90:8000/health`로 상태를 확인함
9. `curl "http://54.180.79.90:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"`로 조회를 확인함
10. 팀원 앱의 Backend URL은 `http://54.180.79.90:8000`을 사용함

EC2 운영은 tmux가 아니라 systemd 서비스 `fitcommit-backend`를 사용함. systemd 서비스는 `/home/ubuntu/fitcommit_backend.env`를 환경변수 파일로 읽도록 구성함.

로컬 Mac에서 EC2로 Backend만 압축/전송할 때는 아래 스크립트를 사용함.

```bash
chmod +x scripts/deploy_backend_to_ec2.sh
./scripts/deploy_backend_to_ec2.sh
```

기본 키 파일 경로는 `~/Downloads/fitcommit-key.pem` 사용함. 키 위치가 다르면 아래처럼 바꿔 실행함.

```bash
KEY_PATH=/path/to/your-key.pem ./scripts/deploy_backend_to_ec2.sh
```

스크립트는 기존 `backend.tar.gz`를 삭제한 뒤 `Backend/.env`, `Backend/.venv`, `Backend/__pycache__`, `Backend/app/**/__pycache__`, `.git`, `.DS_Store`, macOS `._*` 파일을 제외하고 새 `backend.tar.gz`를 만듦. 이후 `ubuntu@54.180.79.90:/home/ubuntu`로 전송함. `.env`는 EC2에서 직접 관리하며 배포 스크립트로 업로드하지 않음.

재시작용 `restart_backend.sh`도 `/home/ubuntu/restart_backend.sh`로 함께 전송함. EC2 안에서는 아래 명령으로 Backend를 교체하고 systemd 서비스를 재시작함.

```bash
ssh -i ~/Downloads/fitcommit-key.pem ubuntu@54.180.79.90
bash /home/ubuntu/restart_backend.sh
```

이미 새 Backend가 반영된 뒤에는 아래 경로의 스크립트도 사용할 수 있음.

```bash
bash /home/ubuntu/Backend/deploy/restart_backend.sh
```

전송 후 바로 재시작까지 실행하려면 아래 옵션을 사용함. 기본 동작은 안전하게 전송만 수행함.

```bash
./scripts/deploy_backend_to_ec2.sh --restart
```

systemd 서비스 관리는 아래 명령을 사용함.

```bash
sudo systemctl status fitcommit-backend --no-pager
sudo systemctl start fitcommit-backend
sudo systemctl stop fitcommit-backend
sudo systemctl restart fitcommit-backend
journalctl -u fitcommit-backend -f
```

서버가 켜져 있어야 팀원 앱에서 `http://54.180.79.90:8000`으로 접속 가능함. 테스트가 끝나면 AWS Console에서 EC2 Stop을 해야 비용이 줄어듦.

팀원 테스트용 Backend URL 기준은 아래와 같음.

```text
iOS 시뮬레이터: http://127.0.0.1:8000
Android 에뮬레이터: http://10.0.2.2:8000
팀원 실제 기기: http://54.180.79.90:8000
```

AI 결과 없이 사진/카테고리만 먼저 저장하는 `POST /clothes` curl 테스트는 아래처럼 함.

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

Storage 업로드 테스트는 아래처럼 함.

```bash
curl -X POST http://54.180.79.90:8000/clothes/upload \
  -F "file=@./sample-clothing.jpg" \
  -F "user_id=45482f0f-42d0-401e-a18a-77abeb0492cc" \
  -F "main_category=top"
```

응답의 `image_path`가 `local://`가 아니라 `45482f0f-.../top/...jpg` 형태인지 확인함. Supabase Dashboard의 Storage `clothing-images` bucket에서도 파일이 보여야 함.

health check는 아래처럼 확인함.

```bash
curl http://54.180.79.90:8000/health
```

AI 성공 후 같은 row를 업데이트하는 `PUT /clothes/{id}` 테스트는 아래처럼 함.

```bash
curl -X PUT http://127.0.0.1:8000/clothes/{clothing_id} \
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

## AI 서버 실행 방법

Python 3.11 사용을 권장함. 첫 CLIP 실행 시 Hugging Face 모델 다운로드가 필요할 수 있음.

```bash
cd AI
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8001
```

AI 서버 상태 확인은 아래처럼 함.

```bash
curl http://127.0.0.1:8001/health
```

`/predict` multipart 테스트는 아래처럼 함.

```bash
curl -X POST http://127.0.0.1:8001/predict \
  -F "file=@/path/to/clothing.jpg" \
  -F "category_hint=top"
```

응답의 `analysis.method`가 `"clip"`이면 CLIP similarity 추론이 실제 사용된 상태임. `"fallback"`이면 CLIP 로딩 또는 추론 실패로 MobileNetV3 fallback이 사용된 상태임.

## iOS 실행 및 설정

iOS 앱은 Xcode에서 `Ios/MyCloset.xcodeproj`를 열어 실행함.

- 사진을 선택하면 SwiftData 로컬 옷장에 먼저 저장함
- Backend 설정이 있으면 로컬 저장 후 Backend `/clothes` 동기화를 시도함
- 팀원 테스트 기본값은 `http://54.180.79.90:8000` 사용함
- 개발 테스트 기본값은 `ENABLE_AI_ANALYSIS=false`라서 AI 서버가 꺼져 있어도 저장됨
- AI 성공/실패 결과는 로컬 데이터에 먼저 반영하고, remote id가 있으면 같은 row에 PUT으로 업데이트함
- Backend 저장 실패 시에도 SwiftData 로컬 데이터는 유지됨
- 개발용 설정 키는 `FITCOMMIT_BACKEND_BASE_URL`, `FITCOMMIT_SUPABASE_USER_ID` 사용함
- iOS 시뮬레이터는 Mac의 localhost에 `http://127.0.0.1:8000`으로 접근 가능함
- 실제 iPhone 테스트 시에는 `http://{Mac의_로컬_IP}:8000` 형태를 사용해야 함

현재 iOS 코드에는 팀원 테스트용 기본값이 들어 있음.

```text
FITCOMMIT_BACKEND_BASE_URL=http://54.180.79.90:8000
FITCOMMIT_SUPABASE_USER_ID=45482f0f-42d0-401e-a18a-77abeb0492cc
```

실제 운영 단계에서는 임시 `user_id`가 아니라 Supabase Auth access token 기반 인증으로 바꿔야 함.

## Android 실행 및 설정

Android 앱은 Android Studio에서 `Android/` 폴더를 열어 실행함.

- 사진을 선택하면 `ClothingStorage` 로컬 옷장에 먼저 저장함
- Backend 설정이 있으면 로컬 저장 후 Backend `/clothes` 동기화를 시도함
- 팀원 테스트 기본값은 `http://54.180.79.90:8000` 사용함
- 개발 테스트 기본값은 `ENABLE_AI_ANALYSIS=false`라서 AI 서버가 꺼져 있어도 저장됨
- AI 성공/실패 결과는 로컬 데이터에 먼저 반영하고, remote id가 있으면 같은 row에 PUT으로 업데이트함
- Backend 저장 실패 시에도 `ClothingStorage` 로컬 데이터는 유지됨
- Android 에뮬레이터에서 Mac localhost에 접근할 때는 `http://10.0.2.2:8000`을 사용해야 함
- 실제 Android 기기는 Mac과 같은 Wi-Fi에 연결한 뒤 `http://{Mac의_로컬_IP}:8000`을 사용해야 함
- 현재 Codex 검증 환경에는 Java Runtime이 없어 Android Gradle 빌드는 확인하지 못함

Android 개발 기본값은 `Android/app/src/main/java/com/fitcommit/app/services/ApiClient.java`에서 관리함.

```text
BASE_URL=http://54.180.79.90:8000
BACKEND_CLOTHES_USER_ID=45482f0f-42d0-401e-a18a-77abeb0492cc
```

## API 명세

### AI 서버

#### `POST /predict`

요청은 `multipart/form-data` 사용함.

```text
file=@/path/to/clothing.jpg
category_hint=top
```

응답 예시는 아래와 같음.

```json
{
  "status": "success",
  "analysis": {
    "main_category": "top",
    "subcategory": "hoodie",
    "main_color": "gray",
    "season": ["fall", "winter"],
    "style_tags": ["casual", "street"],
    "confidence": 0.82,
    "method": "clip"
  }
}
```

### Backend 서버

#### `GET /health`

Backend 상태를 확인함.

```json
{
  "status": "ok",
  "service": "fitcommit-backend"
}
```

#### `POST /clothes`

로컬 저장 이후 서버 연결이 가능할 때 DB 메타데이터를 동기화함. AI 분석 성공은 저장의 필수 조건이 아니며, `image_url`/`image_path` 없이 `local_image_path`만 있어도 row 생성이 가능함.

```json
{
  "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
  "main_category": "top",
  "local_image_path": "file:///local/clothing/top-sample.jpg",
  "analysis_status": "pending",
  "needs_analysis": true,
  "sync_status": "synced",
  "memo": ""
}
```

#### `POST /clothes/upload`

옷 사진을 Supabase Storage `clothing-images` bucket에 업로드함. multipart/form-data로 `file`, `user_id`, `main_category`를 보냄. 성공하면 `image_path`, `image_url`, `bucket`을 반환함.

```bash
curl -X POST http://54.180.79.90:8000/clothes/upload \
  -F "file=@./sample-clothing.jpg" \
  -F "user_id=45482f0f-42d0-401e-a18a-77abeb0492cc" \
  -F "main_category=top"
```

#### `GET /clothes?user_id={uuid}`

특정 사용자의 옷 목록을 최신순으로 조회함.
`wardrobe_id`를 함께 넘기면 특정 옷장에 속한 옷만 조회함.

```bash
curl "http://54.180.79.90:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
curl "http://54.180.79.90:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc&wardrobe_id={wardrobe_id}"
```

#### `GET /clothes/me`

`Authorization: Bearer <supabase_access_token>` 또는 개발용 `X-Test-User-Id`로 현재 사용자의 옷 목록을 조회함. `wardrobe_id` 쿼리를 넘기면 해당 옷장 기준으로 필터링함.

#### `GET /users/me/profile`

현재 사용자의 프로필을 조회함. 운영 구조에서는 Supabase Auth access token을 사용해야 하며, 개발 중에는 `X-Test-User-Id` 헤더를 사용할 수 있음.

#### `POST /users/me/profile`

현재 사용자의 프로필을 생성 또는 upsert함. 프로필 생성 후 기본 옷장이 없으면 `wardrobes`에 `기본 옷장`을 만들 수 있는 구조로 구성됨.

#### `PUT /users/me/profile`

현재 사용자의 `nickname`, `height_cm`, `body_type`, `preferred_style` 값을 수정함.

#### `GET /wardrobes?user_id={uuid}`

사용자의 옷장 목록을 조회함.

```bash
curl "http://54.180.79.90:8000/wardrobes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

#### `GET /wardrobes/default?user_id={uuid}`

사용자의 기본 옷장을 조회함. 기본 옷장이 없으면 Backend가 자동 생성함.

```bash
curl "http://54.180.79.90:8000/wardrobes/default?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

#### `POST /wardrobes`

추가 옷장을 생성함. 같은 사용자 안에서 같은 `name`은 중복될 수 없음.

```bash
curl -X POST "http://54.180.79.90:8000/wardrobes" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
    "name": "여름 옷장",
    "description": "여름 아이템 모음",
    "is_default": false
  }'
```

#### `PUT /wardrobes/{wardrobe_id}`

옷장 이름과 설명을 수정함. 기본 옷장의 `is_default`를 직접 해제하거나 기본 옷장을 삭제하는 동작은 막음.

#### `DELETE /wardrobes/{wardrobe_id}`

추가 옷장을 삭제함. 기본 옷장은 삭제할 수 없음.

#### `GET /clothes/{clothing_id}`

옷 단건을 조회함. 필요하면 `user_id` 쿼리로 소유자 필터를 함께 사용함.

#### `PUT /clothes/{clothing_id}`

옷 정보를 수정함. AI 성공 시 completed 업데이트, AI 실패 시 failed 업데이트, 사용자가 수정한 최종값 저장에 사용함.

```bash
curl -X PUT "http://54.180.79.90:8000/clothes/{clothing_id}?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc" \
  -H "Content-Type: application/json" \
  -d '{
    "main_category": "top",
    "subcategory": "hoodie",
    "main_color": "black",
    "season": ["fall", "winter"],
    "style_tags": ["casual", "street"],
    "analysis_method": "manual",
    "analysis_status": "pending",
    "needs_analysis": true,
    "sync_status": "synced",
    "memo": "manual_edit"
  }'
```

수정 후에는 `GET /clothes/{clothing_id}?user_id=...`로 변경값을 확인함.

#### `POST /clothes/{clothing_id}/analyze`

재분석 예약용 API임. 현재는 row의 `image_url`/`image_path`를 확인하는 구조만 열려 있고, Supabase Storage download와 AI 서버 재전송은 TODO 상태임.

#### `DELETE /clothes/{clothing_id}`

옷 정보를 삭제함.

## 현재 테스트 완료 상태

- EC2 Backend 배포 완료함
- systemd 서비스 `fitcommit-backend` 실행 완료함
- Backend ↔ Supabase `GET /clothes`, `POST /clothes` curl 테스트 성공함
- Supabase `public.clothes` row 저장/조회 성공함
- AI 없이 `analysis_status=pending`, `needs_analysis=true`, `sync_status=synced` 상태 저장 구조 완료함
- Supabase Storage `clothing-images` 업로드 API 추가 완료함
- Backend Python 문법 검사 성공함
- AI 서버 Python 문법 검사 성공함
- AI `fashion_clip_service.py` import 확인 성공함
- iOS Debug 빌드 성공함
- Android Gradle 빌드는 현재 Codex 환경에 Java Runtime이 없어 검증하지 못함

## End-to-End 테스트 순서

1. EC2 Backend `http://54.180.79.90:8000`을 사용함
2. AI 서버는 실행하지 않아도 됨
3. iOS 시뮬레이터에서 옷 추가를 진행함
4. EC2 Backend에 `POST /clothes` 요청이 성공하는지 확인함
5. Supabase `public.clothes` 테이블에 `analysis_status=pending`, `needs_analysis=true` row가 생성되는지 확인함
6. 앱 옷장 목록에 저장된 item이 표시되는지 확인함
7. AI 분석은 `ENABLE_AI_ANALYSIS=true`로 바꾼 뒤 별도로 실행함
8. 로컬 Android 에뮬레이터 테스트 시 Backend 주소는 `http://10.0.2.2:8000`을 사용할 수 있음

## 앞으로 남은 작업

- 실제 AI 서버를 배포해야 함
- 실제 Storage 업로드 end-to-end를 앱에서 검증해야 함
- MobileNetV3/CLIP/FashionCLIP 분석을 운영 서버와 앱 후처리 흐름에 실제 연결하고 성능 검증해야 함
- 업로드된 이미지 기반 AI 재분석과 벡터화를 연결해야 함
- 배경 제거 이미지를 별도 Storage path로 저장하려면 원본 이미지 path와 배경 제거 이미지 path를 구분하는 schema를 설계해야 함
- `POST /clothes/{clothing_id}/analyze`에서 Storage 이미지를 내려받아 AI 서버로 재전송하는 재분석 흐름을 완성해야 함
- iOS/Android 앱에서 실제 저장/조회 end-to-end를 더 넓은 케이스로 검증해야 함
- Android JDK 환경에서 Gradle 빌드를 검증해야 함
- 팀원 테스트 단계에서는 같은 테스트 user_id를 쓰면 옷 데이터가 같은 계정에 섞일 수 있음
- 임시 `user_id`/`X-Test-User-Id` 방식에서 Supabase Auth access token 기반 인증으로 개선해야 함
- 가입 또는 로그인 후 `user_profiles` 생성, 기본 `wardrobes` 생성, `clothes.wardrobe_id` 연결 흐름을 앱에 붙여야 함
- 웹은 아직 구현하지 않았지만 프로필 조회, 옷장 목록 조회, 기본 옷장 조회, 옷 등록 시 `wardrobe_id` 선택 흐름을 같은 Backend API로 사용해야 함
- Elastic IP 또는 도메인 연결을 적용해야 함
- YOLO 기반 전신 사진 자동 분리는 1차 프로토타입 범위 밖이며, 필요하면 향후 고도화 기능으로 다시 검토함

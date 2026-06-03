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
  ├─ AWS EC2: http://13.125.252.131:8000
  └─ /clothes CRUD
       └─ Supabase public.clothes 저장/조회/수정/삭제

Supabase
  ├─ PostgreSQL public.clothes
  └─ Storage clothing-images bucket
```

AI 서버와 Backend 서버는 분리되어 있음. Backend는 현재 AWS EC2에서 `http://13.125.252.131:8000` 주소로 실행 중임. 앱은 사진과 기본 카테고리를 로컬에 먼저 저장하고, EC2 Backend를 통해 Supabase에 메타데이터를 동기화함. AI 서버는 아직 배포하지 않았고, 현재 팀원 테스트는 AI 분석 비활성화 상태로 저장/조회만 확인함.

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
│   ├── MyClosetUITests/
│   └── README.md
├── Frontend/
│   └── .gitkeep
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
- Backend 연결이 가능하면 `/clothes`로 DB 메타데이터 동기화를 시도함
- Backend 저장 성공 시 로컬 데이터에 `remote_id`를 저장하고 `sync_status=synced`로 업데이트함
- Backend 저장 실패 시 로컬 데이터는 유지하고 `sync_status=sync_failed`, `sync_error`를 저장함
- `ENABLE_AI_ANALYSIS=false`이면 앱은 AI 서버 `/predict`를 호출하지 않고 `analysis_status=pending` 상태로 저장만 함
- `ENABLE_AI_ANALYSIS=true`이면 저장 후 AI 서버 `/predict`로 `file`과 `category_hint`를 multipart로 전송함
- AI 성공 시 앱은 Backend `PUT /clothes/{clothing_id}`로 `analysis_status=completed`, `needs_analysis=false`, `analyzed_at`과 분석 결과를 업데이트함
- AI 실패 시 앱은 같은 row를 유지하고 `analysis_status=failed`, `needs_analysis=true`, `analysis_error`를 저장함
- AI 실패 또는 서버 실패가 발생해도 옷장 화면에는 로컬 아이템이 남아 있어야 함
- 앱에서 AI 분석 결과를 확인/수정한 뒤 사용자가 확정한 값으로 로컬과 서버 row를 다시 업데이트함
- Supabase Storage 이미지 업로드는 아직 연결되지 않았고, 현재는 로컬 사진 저장 + DB 메타데이터 동기화 구조임
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
- RLS 정책은 로그인한 사용자가 자기 옷만 접근할 수 있도록 설정함
- Backend는 서버 내부에서만 `SUPABASE_SERVICE_ROLE_KEY`를 사용함
- service role key나 secret key는 iOS/Android 코드에 절대 넣으면 안 됨
- Storage bucket 이름은 `clothing-images` 사용함
- Storage 업로드는 아직 미구현 상태임
- 현재는 로컬 사진 저장 + DB 메타데이터 동기화만 사용함
- 다음 단계에서 `clothing-images` bucket에 이미지를 업로드해야 함
- 이후 업로드된 이미지 기반 AI 재분석과 벡터화를 연결할 예정임
- 향후 Storage 경로는 `{user_id}/{main_category}/{filename}` 형태로 만들 예정임
- 분석/동기화 상태 관리를 위해 `analysis_status`, `analysis_error`, `analyzed_at`, `needs_analysis`, `sync_status`, `local_image_path` 컬럼을 사용함
- 기존 Supabase 테이블에는 `Backend/app/db/clothes_analysis_status_migration.sql`을 SQL Editor에서 실행해 컬럼을 추가함

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
http://13.125.252.131:8000
```

외부 Mac 터미널에서 Supabase DB 연동 `GET /clothes`, `POST /clothes` 200 OK 확인 완료함. 자세한 절차는 `Backend/README.md`의 `AWS EC2 배포` 섹션을 기준으로 함.

1. Ubuntu EC2 인스턴스를 생성함
2. 보안 그룹 inbound rule에 `22/tcp`, `8000/tcp`를 허용함
3. EC2에 SSH 접속 후 GitHub 저장소를 clone함
4. `Backend/`에서 Python 3.11 venv를 생성함
5. `pip install -r requirements.txt`를 실행함
6. `.env.example`을 복사해 `.env`를 작성함
7. `python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`으로 실행함
8. `curl http://13.125.252.131:8000/health`로 상태를 확인함
9. `curl "http://13.125.252.131:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"`로 조회를 확인함
10. 팀원 앱의 Backend URL은 `http://13.125.252.131:8000`을 사용함

EC2에서 터미널을 닫아도 실행되게 하려면 `Backend/deploy/fitcommit-backend.service.example` systemd 예시를 사용함.

팀원 테스트용 Backend URL 기준은 아래와 같음.

```text
iOS 시뮬레이터: http://127.0.0.1:8000
Android 에뮬레이터: http://10.0.2.2:8000
팀원 실제 기기: http://13.125.252.131:8000
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
    "sync_status": "pending_sync",
    "memo": ""
  }'
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
- 팀원 테스트 기본값은 `http://13.125.252.131:8000` 사용함
- 개발 테스트 기본값은 `ENABLE_AI_ANALYSIS=false`라서 AI 서버가 꺼져 있어도 저장됨
- AI 성공/실패 결과는 로컬 데이터에 먼저 반영하고, remote id가 있으면 같은 row에 PUT으로 업데이트함
- Backend 저장 실패 시에도 SwiftData 로컬 데이터는 유지됨
- 개발용 설정 키는 `FITCOMMIT_BACKEND_BASE_URL`, `FITCOMMIT_SUPABASE_USER_ID` 사용함
- iOS 시뮬레이터는 Mac의 localhost에 `http://127.0.0.1:8000`으로 접근 가능함
- 실제 iPhone 테스트 시에는 `http://{Mac의_로컬_IP}:8000` 형태를 사용해야 함

현재 iOS 코드에는 팀원 테스트용 기본값이 들어 있음.

```text
FITCOMMIT_BACKEND_BASE_URL=http://13.125.252.131:8000
FITCOMMIT_SUPABASE_USER_ID=45482f0f-42d0-401e-a18a-77abeb0492cc
```

실제 운영 단계에서는 임시 `user_id`가 아니라 Supabase Auth access token 기반 인증으로 바꿔야 함.

## Android 실행 및 설정

Android 앱은 Android Studio에서 `Android/` 폴더를 열어 실행함.

- 사진을 선택하면 `ClothingStorage` 로컬 옷장에 먼저 저장함
- Backend 설정이 있으면 로컬 저장 후 Backend `/clothes` 동기화를 시도함
- 팀원 테스트 기본값은 `http://13.125.252.131:8000` 사용함
- 개발 테스트 기본값은 `ENABLE_AI_ANALYSIS=false`라서 AI 서버가 꺼져 있어도 저장됨
- AI 성공/실패 결과는 로컬 데이터에 먼저 반영하고, remote id가 있으면 같은 row에 PUT으로 업데이트함
- Backend 저장 실패 시에도 `ClothingStorage` 로컬 데이터는 유지됨
- Android 에뮬레이터에서 Mac localhost에 접근할 때는 `http://10.0.2.2:8000`을 사용해야 함
- 실제 Android 기기는 Mac과 같은 Wi-Fi에 연결한 뒤 `http://{Mac의_로컬_IP}:8000`을 사용해야 함
- 현재 Codex 검증 환경에는 Java Runtime이 없어 Android Gradle 빌드는 확인하지 못함

Android 개발 기본값은 `Android/app/src/main/java/com/fitcommit/app/services/ApiClient.java`에서 관리함.

```text
BASE_URL=http://13.125.252.131:8000
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

#### `POST /clothes`

로컬 저장 이후 서버 연결이 가능할 때 DB 메타데이터를 동기화함. AI 분석 성공은 저장의 필수 조건이 아니며, `image_url`/`image_path` 없이 `local_image_path`만 있어도 row 생성이 가능함.

```json
{
  "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
  "main_category": "top",
  "local_image_path": "file:///local/clothing/top-sample.jpg",
  "analysis_status": "pending",
  "needs_analysis": true,
  "sync_status": "pending_sync",
  "memo": ""
}
```

#### `GET /clothes?user_id={uuid}`

특정 사용자의 옷 목록을 최신순으로 조회함.

#### `GET /clothes/{clothing_id}`

옷 단건을 조회함. 필요하면 `user_id` 쿼리로 소유자 필터를 함께 사용함.

#### `PUT /clothes/{clothing_id}`

옷 정보를 수정함. AI 성공 시 completed 업데이트, AI 실패 시 failed 업데이트, 사용자가 수정한 최종값 저장에 사용함.

#### `POST /clothes/{clothing_id}/analyze`

재분석 예약용 API임. 현재는 row의 `image_url`/`image_path`를 확인하는 구조만 열려 있고, Supabase Storage download와 AI 서버 재전송은 TODO 상태임.

#### `DELETE /clothes/{clothing_id}`

옷 정보를 삭제함.

## 현재 테스트 완료 상태

- Backend ↔ Supabase `POST /clothes` curl 테스트 성공함
- Supabase `public.clothes` row 저장 성공함
- Backend Python 문법 검사 성공함
- AI 서버 Python 문법 검사 성공함
- AI `fashion_clip_service.py` import 확인 성공함
- iOS Debug 빌드 성공함
- Android Gradle 빌드는 현재 Codex 환경에 Java Runtime이 없어 검증하지 못함

## End-to-End 테스트 순서

1. EC2 Backend `http://13.125.252.131:8000`을 사용함
2. AI 서버는 실행하지 않아도 됨
3. iOS 시뮬레이터에서 옷 추가를 진행함
4. EC2 Backend에 `POST /clothes` 요청이 성공하는지 확인함
5. Supabase `public.clothes` 테이블에 `analysis_status=pending`, `needs_analysis=true` row가 생성되는지 확인함
6. 앱 옷장 목록에 저장된 item이 표시되는지 확인함
7. AI 분석은 `ENABLE_AI_ANALYSIS=true`로 바꾼 뒤 별도로 실행함
8. 로컬 Android 에뮬레이터 테스트 시 Backend 주소는 `http://10.0.2.2:8000`을 사용할 수 있음

## 앞으로 남은 작업

- FashionCLIP 전용 모델을 실제 데이터에 맞춰 선택하고 성능 검증해야 함
- Supabase Storage `clothing-images` 이미지 업로드를 연결해야 함
- 업로드된 이미지 기반 AI 재분석과 벡터화를 연결해야 함
- `POST /clothes/{clothing_id}/analyze`에서 Storage 이미지를 내려받아 AI 서버로 재전송하는 재분석 흐름을 완성해야 함
- iOS/Android 앱에서 실제 저장/조회 end-to-end를 더 넓은 케이스로 검증해야 함
- Android JDK 환경에서 Gradle 빌드를 검증해야 함
- 임시 `user_id` 방식에서 Supabase Auth access token 기반 인증으로 개선해야 함
- AWS 또는 배포 서버에 Backend/AI 서버를 배포해야 함
- YOLO 기반 전신 사진 자동 분리는 1차 프로토타입 범위 밖이며, 필요하면 향후 고도화 기능으로 다시 검토함

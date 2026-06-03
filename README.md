# Fitcommit

Fitcommit은 사용자가 가진 옷을 등록하고, AI가 옷 정보를 분석한 뒤, 사용자의 옷장 데이터를 기반으로 코디 추천을 제공하는 앱임.

1차 프로토타입에서는 전신 사진 분석과 YOLO 기반 상하의 자동 분리를 제외함. 현재 구조는 사용자가 상의, 하의, 아우터, 신발, 모자, 가방, 원피스 같은 큰 카테고리를 먼저 고르고, 아이템별 사진을 따로 촬영하거나 업로드하는 방식으로 구성됨.

## 현재 아키텍처

```text
iOS 앱 / Android 앱
  ├─ 카테고리 선택
  ├─ 아이템 단독 사진 촬영 또는 업로드
  ├─ AI 서버 POST /predict 호출
  │    └─ multipart/form-data: file + category_hint
  ├─ 분석 결과 확인 및 수정
  └─ Backend POST /clothes 호출
       └─ 사용자가 최종 확정한 값 저장

Python FastAPI AI 서버
  └─ /predict
       ├─ CLIP 이미지-텍스트 similarity 기반 세부 카테고리 추론
       ├─ MobileNetV3 fallback
       ├─ 대표 색상 추출
       └─ 시즌/스타일 태그 반환

Python FastAPI Backend 서버
  └─ /clothes CRUD
       └─ Supabase public.clothes 저장/조회/수정/삭제

Supabase
  ├─ PostgreSQL public.clothes
  └─ Storage clothing-images bucket
```

AI 서버와 Backend 서버는 분리되어 있음. AI 서버는 이미지 분석 결과만 반환하고, Backend 서버는 사용자가 확정한 옷장 데이터를 Supabase에 저장함.

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
- 앱에서 AI 서버 `/predict`로 `file`과 `category_hint`를 multipart로 전송함
- 앱에서 AI 분석 결과를 바로 저장하지 않고 확인/수정 화면을 보여줌
- 사용자가 확정한 종류, 세부 종류, 대표 색상, 계절, 스타일 태그, 신뢰도를 Backend `/clothes`로 저장함
- Backend는 Supabase `public.clothes` 테이블에 데이터를 저장함
- Backend 저장 실패 시 iOS는 SwiftData, Android는 `ClothingStorage` 로컬 저장소를 fallback으로 사용함
- Supabase Storage 이미지 업로드는 아직 연결되지 않았고, 현재는 `image_url` 또는 `image_path`에 임시 이미지 참조값을 저장함

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
- 향후 Storage 경로는 `{user_id}/{main_category}/{filename}` 형태로 만들 예정임

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

Backend `.env` 예시는 아래처럼 작성함. 실제 키 값은 README에 적지 않아야 함.

```env
SUPABASE_URL=https://{project_ref}.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_CLOTHING_BUCKET=clothing-images
```

`SUPABASE_SERVICE_ROLE_KEY`는 Backend 전용 키임. iOS/Android 앱, GitHub, 공개 문서에 넣으면 안 됨.

`POST /clothes` curl 테스트는 아래처럼 함.

```bash
curl -X POST http://127.0.0.1:8000/clothes \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
    "main_category": "top",
    "subcategory": "hoodie",
    "main_color": "gray",
    "season": ["fall", "winter"],
    "style_tags": ["casual", "street"],
    "image_url": "https://example.com/clothing.jpg",
    "image_path": "45482f0f-42d0-401e-a18a-77abeb0492cc/top/sample.jpg",
    "confidence": 0.82,
    "analysis_method": "clip",
    "memo": ""
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

- Backend 설정이 있으면 저장 시 Backend `/clothes`를 우선 호출함
- Backend 저장 실패 시 SwiftData 로컬 저장 fallback을 사용함
- 개발용 설정 키는 `FITCOMMIT_BACKEND_BASE_URL`, `FITCOMMIT_SUPABASE_USER_ID` 사용함
- iOS 시뮬레이터는 Mac의 localhost에 `http://127.0.0.1:8000`으로 접근 가능함
- 실제 iPhone 테스트 시에는 `http://{Mac의_로컬_IP}:8000` 형태를 사용해야 함

현재 iOS 코드에는 개발 기본값이 들어 있음.

```text
FITCOMMIT_BACKEND_BASE_URL=http://127.0.0.1:8000
FITCOMMIT_SUPABASE_USER_ID=45482f0f-42d0-401e-a18a-77abeb0492cc
```

실제 운영 단계에서는 임시 `user_id`가 아니라 Supabase Auth access token 기반 인증으로 바꿔야 함.

## Android 실행 및 설정

Android 앱은 Android Studio에서 `Android/` 폴더를 열어 실행함.

- Backend 설정이 있으면 저장 시 Backend `/clothes`를 우선 호출함
- Backend 저장 실패 시 `ClothingStorage` 로컬 저장 fallback을 사용함
- Android 에뮬레이터에서 Mac localhost에 접근할 때는 `http://10.0.2.2:8000`을 사용해야 함
- 실제 Android 기기는 Mac과 같은 Wi-Fi에 연결한 뒤 `http://{Mac의_로컬_IP}:8000`을 사용해야 함
- 현재 Codex 검증 환경에는 Java Runtime이 없어 Android Gradle 빌드는 확인하지 못함

Android 개발 기본값은 `Android/app/src/main/java/com/fitcommit/app/services/ApiClient.java`에서 관리함.

```text
BASE_URL=http://10.0.2.2:8000
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

사용자가 최종 확정한 옷 정보를 저장함.

```json
{
  "user_id": "45482f0f-42d0-401e-a18a-77abeb0492cc",
  "main_category": "top",
  "subcategory": "hoodie",
  "main_color": "gray",
  "season": ["fall", "winter"],
  "style_tags": ["casual", "street"],
  "image_url": "https://example.com/clothing.jpg",
  "image_path": "45482f0f-42d0-401e-a18a-77abeb0492cc/top/sample.jpg",
  "confidence": 0.82,
  "analysis_method": "clip",
  "memo": ""
}
```

#### `GET /clothes?user_id={uuid}`

특정 사용자의 옷 목록을 최신순으로 조회함.

#### `GET /clothes/{clothing_id}`

옷 단건을 조회함. 필요하면 `user_id` 쿼리로 소유자 필터를 함께 사용함.

#### `PUT /clothes/{clothing_id}`

옷 정보를 수정함. 앱에서 사용자가 수정한 최종 값을 보낼 때 사용함.

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

1. Backend를 `http://127.0.0.1:8000`으로 실행함
2. AI 서버를 별도 포트로 실행하고 앱의 AI 서버 주소를 맞춤
3. iOS 시뮬레이터에서 옷을 저장함
4. Supabase `public.clothes` 테이블에 row가 생성됐는지 확인함
5. 앱을 재실행한 뒤 Backend에서 옷 목록이 조회되는지 확인함
6. Android 에뮬레이터 테스트 시 Backend 주소는 `http://10.0.2.2:8000`을 사용함

## 앞으로 남은 작업

- FashionCLIP 전용 모델을 실제 데이터에 맞춰 선택하고 성능 검증해야 함
- Supabase Storage `clothing-images` 이미지 업로드를 연결해야 함
- iOS/Android 앱에서 실제 저장/조회 end-to-end를 더 넓은 케이스로 검증해야 함
- Android JDK 환경에서 Gradle 빌드를 검증해야 함
- 임시 `user_id` 방식에서 Supabase Auth access token 기반 인증으로 개선해야 함
- AWS 또는 배포 서버에 Backend/AI 서버를 배포해야 함
- YOLO 기반 전신 사진 자동 분리는 1차 프로토타입 범위 밖이며, 필요하면 향후 고도화 기능으로 다시 검토함

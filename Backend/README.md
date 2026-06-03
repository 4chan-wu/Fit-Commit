# Fitcommit Backend

Fitcommit Backend는 앱에서 확정한 옷장 데이터를 Supabase에 저장하고 조회하는 FastAPI 서버임. 기존 사용자, 채팅, 결제 API도 같은 서버 안에 남아 있지만, 현재 1차 프로토타입의 핵심은 `/clothes` API와 Supabase `public.clothes` 연동임.

## 역할

- iOS/Android 앱에서 보낸 최종 의류 분석 결과를 저장함
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
```

- `SUPABASE_URL`에는 Supabase Dashboard 주소가 아니라 API URL을 넣어야 함
- Dashboard 주소 예시는 `https://supabase.com/dashboard/project/{project_ref}` 형태임
- API URL 예시는 `https://{project_ref}.supabase.co` 형태임
- `SUPABASE_SERVICE_ROLE_KEY`는 RLS를 우회할 수 있는 민감한 키이므로 Backend `.env`에만 보관해야 함
- iOS/Android 코드에는 service role key를 절대 넣으면 안 됨

기존 사용자/채팅/결제 기능을 함께 사용할 때는 `.env.example`의 `DATABASE_URL`, `SECRET_KEY`, `OPENAI_API_KEY`, Toss 관련 값을 추가로 설정함.

## Supabase 테이블

Backend는 Supabase `public.clothes` 테이블을 사용함.

- `user_id`는 `auth.users(id)`를 참조하는 `uuid` 타입임
- RLS 정책은 로그인한 사용자가 자기 옷만 접근하도록 설정함
- Backend는 service role key로 서버 측 저장/조회 요청을 수행함
- Storage bucket 이름은 `clothing-images` 사용함
- Storage 업로드는 아직 미구현 상태임
- 향후 이미지 경로는 `{user_id}/{main_category}/{filename}` 형태로 구성할 예정임

## 실행 방법

Python 3.11 사용을 권장함.

```bash
cd Backend
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

상태 확인은 아래처럼 함.

```bash
curl http://127.0.0.1:8000/health
```

## `/clothes` API

### `POST /clothes`

사용자가 앱에서 최종 확정한 의류 정보를 Supabase에 저장함.

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
    "main_color": "black",
    "memo": "자주 입는 후드"
  }'
```

### `DELETE /clothes/{clothing_id}`

옷 정보를 삭제함.

```bash
curl -X DELETE "http://127.0.0.1:8000/clothes/{clothing_id}?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc"
```

## 앱 연동 기준

- iOS 시뮬레이터는 Backend 주소로 `http://127.0.0.1:8000` 사용 가능함
- Android 에뮬레이터는 Backend 주소로 `http://10.0.2.2:8000` 사용해야 함
- 실제 iPhone/Android 기기는 Mac과 같은 Wi-Fi에 연결한 뒤 `http://{Mac의_로컬_IP}:8000` 사용해야 함
- 앱은 Backend 저장을 먼저 시도하고 실패하면 로컬 저장 fallback을 사용함

## 검증 상태

- Backend ↔ Supabase `POST /clothes` curl 테스트 성공함
- Supabase `public.clothes` row 저장 성공함
- Backend Python 문법 검사 성공함
- iOS 저장/조회 흐름은 Backend 우선, SwiftData fallback 구조로 연결됨
- Android 저장/조회 흐름은 Backend 우선, `ClothingStorage` fallback 구조로 연결됨

## 남은 작업

- Supabase Storage `clothing-images` 업로드 API를 연결해야 함
- 임시 `user_id` 쿼리/바디 방식에서 Supabase Auth access token 기반 인증으로 개선해야 함
- 운영 배포 환경에서는 service role key 관리와 CORS 정책을 다시 점검해야 함

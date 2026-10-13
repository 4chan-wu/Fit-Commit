# Fitcommit Android

Fitcommit Android는 Java/XML 기반 Android 앱임. 현재 흐름은 iOS와 동일하게 카테고리를 먼저 선택하고, 아이템 단독 사진을 로컬 옷장에 먼저 저장한 뒤 Backend 동기화와 AI 분석을 후처리로 시도하는 구조임.

## 역할

- 사용자가 의류 큰 카테고리를 선택함
- 아이템 단독 사진 촬영/업로드 흐름을 제공함
- AI 서버 호출 전에 `ClothingStorage` 로컬 옷장에 먼저 저장함
- 로컬 저장 후 Backend `/clothes` 동기화를 시도함
- `ENABLE_AI_ANALYSIS=false`이면 AI 서버 `/predict`를 호출하지 않음
- `ENABLE_AI_ANALYSIS=true`일 때만 AI 서버 `/predict`에 `file`과 `category_hint`를 multipart로 전송함
- AI 성공 시 로컬 데이터를 `completed`로 업데이트하고, remote id가 있으면 같은 row를 업데이트함
- AI 실패 시 로컬 데이터를 `failed`로 유지하고, remote id가 있으면 `analysis_error`를 저장함
- 분석 결과 화면에서 종류, 세부 종류, 대표 색상, 계절, 스타일 태그, 신뢰도를 확인/수정함
- 저장 버튼을 누르면 사용자가 확정한 값을 `ClothingStorage`에 먼저 반영하고, remote id가 있으면 Backend `PUT /clothes/{clothing_id}`로 저장함
- AI 서버 없이도 사용자가 카테고리, 세부 종류, 대표 색상, 계절을 직접 수정할 수 있음
- 대표색은 Android 로컬 이미지 처리로 추천값을 계산하고, 사용자가 직접 바꿀 수 있음
- 수동 수정은 `analysis_method=manual`로 저장하고 `analysis_status=pending`을 유지할 수 있음
- 배경 제거는 저장 필수 조건이 아니며 향후 선택 기능으로 분리함
- 원본 이미지는 항상 먼저 저장하고, 배경 제거 실패 시에도 원본 이미지와 clothes row는 유지해야 함
- Android에는 밝은 배경을 투명 처리하는 `BackgroundRemover.processBitmap/processUri`가 있지만 현재 등록 저장 흐름에는 연결하지 않음
- Android 배경 제거는 ML Kit, OpenCV, 또는 서버 처리 중 하나로 안정화한 뒤 연결해야 함
- Backend 저장 실패 시에도 `ClothingStorage` 로컬 item은 유지됨
- 옷장 화면은 Backend 조회 결과와 로컬 저장소를 remote id/local id 기준으로 병합함
- 3D 공간 스캔과 신체 비율 측정 진입점은 옷장 화면에 계속 유지함

## 구조

```text
Android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/fitcommit/app/
│       │   ├── activities/
│       │   │   └── AnalysisResultActivity.java
│       │   ├── models/
│       │   ├── services/
│       │   │   ├── ApiClient.java
│       │   │   ├── ClothesBackendService.java
│       │   │   ├── ClothingStorage.java
│       │   │   └── FashionAiService.java
│       │   └── utils/
│       └── res/
│           └── layout/
├── gradlew
└── README.md
```

## 실행 방법

Android Studio에서 `Android/` 폴더를 열고 Gradle Sync 후 `app` 실행 구성을 실행함.

로컬에서 Gradle로 확인하려면 JDK가 필요함.

```bash
cd Android
sh gradlew assembleDebug
```

현재 Codex 검증 환경에는 Java Runtime이 없어 위 빌드는 실행하지 못함. 로컬 개발 환경에서는 Android Studio가 설치한 JDK 또는 별도 JDK 17 이상을 사용하면 됨.

## Backend URL 설정

Android 앱의 Backend base URL은 `app/src/main/java/com/fitcommit/app/services/ApiClient.java`에서 한 곳으로 관리함.

팀원 실제 기기 테스트용 기본값은 아래와 같음.

```text
BASE_URL=http://54.180.79.90:8000
BACKEND_CLOTHES_USER_ID=45482f0f-42d0-401e-a18a-77abeb0492cc (Debug 빌드 전용)
ENABLE_AI_ANALYSIS=false
```

`ENABLE_AI_ANALYSIS=false`이면 AI 서버 `/predict`를 호출하지 않고 Backend `/clothes` 저장까지만 진행함.
개발 단계에서는 Debug 빌드만 `BACKEND_CLOTHES_USER_ID`를 `X-Test-User-Id` 헤더로 함께 보냄. Release 빌드에서는 테스트 user_id가 비활성화됨.
Supabase 로그인 세션이 저장되면 테스트 ID 대신 `Authorization: Bearer <token>`을 보내고 `/clothes/me`, `/wardrobes/me`를 사용함.
팀원들이 같은 테스트 user_id를 쓰면 Supabase `clothes` 데이터가 같은 사용자 옷장에 섞일 수 있음.

## 사용자별 옷장 구조

Android 앱은 현재 `wardrobe_id`를 몰라도 옷 등록이 가능함. Backend가 `POST /clothes` 저장 시 사용자 기본 옷장을 자동 생성 또는 조회해서 `clothes.wardrobe_id`에 연결함.

- 사용자는 여러 옷장을 가질 수 있음
- 기본 옷장은 사용자당 1개만 가능함
- 추가 옷장은 여러 개 가능함
- 같은 사용자 안에서 같은 옷장 이름은 중복될 수 없음
- 옷장 화면에서 “전체”와 서버 옷장 목록을 필터 버튼으로 표시함
- 로그인 사용자가 특정 옷장을 선택하면 Backend `GET /clothes/me?wardrobe_id=...`로 해당 옷장 옷만 조회함
- Debug 테스트 사용자는 기존 `GET /clothes?user_id=...&wardrobe_id=...` 경로를 사용함
- “옷장 관리” 버튼으로 추가 옷장을 생성할 수 있음
- 옷장 필터 버튼을 길게 누르면 해당 옷장 수정/삭제 액션을 열 수 있음
- `ClothesBackendService`에는 옷장 목록 조회, 생성, 수정, 삭제 API 호출 구조를 준비함
- 등록/수정 화면의 `wardrobeSpinner`에서 저장할 옷장을 선택할 수 있음
- 선택값이 없으면 `wardrobe_id` 없이 저장하고 Backend가 기본 옷장을 자동 연결함
- 기본 옷장은 삭제할 수 없음
- 추가 옷장 삭제 시 Backend가 해당 옷장의 clothes를 기본 옷장으로 이동함
- 웹도 같은 Backend wardrobe API를 사용해야 함

Android 에뮬레이터에서 로컬 Mac Backend를 테스트할 때는 `10.0.2.2`를 사용해야 함.

```text
Android Emulator -> http://10.0.2.2:8000 -> Backend FastAPI
```

실제 Android 기기에서는 Mac과 같은 Wi-Fi에 연결한 뒤 Backend 주소를 Mac의 로컬 IP로 바꿔야 함.

```text
예시: http://192.168.0.10:8000
```

현재 EC2 Backend 주소는 아래와 같음.

```text
http://54.180.79.90:8000
```

Mac의 로컬 IP는 아래 명령으로 확인할 수 있음.

```bash
ipconfig getifaddr en0
```

## 저장 및 조회 흐름

저장 흐름은 아래 순서로 동작함. AI 분석 성공과 서버 저장 성공은 로컬 저장의 필수 조건이 아님.

```text
AnalysisResultActivity
  -> ClothingStorage에 local item 먼저 저장
  -> Backend POST /clothes/upload로 Storage 업로드 시도
  -> 업로드 성공 시 image_path/image_url을 서버 row에 사용
  -> 업로드 실패 시 local:// fallback 사용
  -> ClothesBackendService.createPending()으로 Backend 동기화 시도
  -> 성공 시 remote_id 저장 + sync_status=synced
  -> 실패 시 로컬 유지 + sync_status=sync_failed
  -> ENABLE_AI_ANALYSIS=false이면 AI 서버 호출 건너뜀
  -> ENABLE_AI_ANALYSIS=true이면 FashionAiService.analyze()
  -> 성공 시 로컬 completed 업데이트 + remote id가 있으면 PUT /clothes/{id}
  -> 실패 시 로컬 failed 업데이트 + remote id가 있으면 PUT /clothes/{id}
  -> 저장 버튼에서 확정값을 로컬/서버에 업데이트
```

조회 흐름은 아래 순서로 동작함.

```text
Closet 화면
  -> ClothingStorage 로컬 데이터 조회
  -> ClothesBackendService.fetchClothes()
  -> Supabase 세션이 있으면 GET /clothes/me
  -> 세션이 없고 Debug 빌드이면 GET /clothes?user_id=...
  -> 성공 시 Supabase 데이터와 로컬 데이터를 중복 없이 병합
  -> 실패 시 ClothingStorage 로컬 데이터만 표시
```

AI 실패 item과 서버 동기화 실패 item도 옷장 화면에 표시됨. 실패 item은 “분석 실패 · 다시 분석 가능”, 서버 실패 item은 “서버 동기화 실패” 상태로 표시됨.

## 이미지 경로

Android는 사진을 `ClothingStorage`에 먼저 저장한 뒤 Backend `POST /clothes/upload`로 Supabase Storage 업로드를 시도함. 업로드 성공 시 `/clothes` 생성 payload에 `image_path`와 `image_url`을 포함함.

Storage 경로는 아래 형태를 사용함.

```text
{user_id}/{main_category}/{filename}.jpg
```

업로드 실패 시에도 옷 등록은 계속 진행함. 이때 `local://...` 또는 앱 내부 로컬 경로를 fallback으로 저장함. `local://` 경로는 Supabase Dashboard에서 볼 수 없는 앱 내부 참조임.

## AI 서버 연동

AI 서버 `/predict` 요청은 multipart로 구성함.

```text
file=image
category_hint=top
```

허용 카테고리는 `top`, `bottom`, `outer`, `shoes`, `hat`, `bag`, `dress` 사용함.
현재 Supabase 저장 테스트 단계에서는 `ENABLE_AI_ANALYSIS=false`로 두고 AI 서버를 실행하지 않아도 됨.

## Supabase 저장 테스트 순서

1. EC2 Backend `http://54.180.79.90:8000`을 사용함
2. AI 서버는 실행하지 않아도 됨
3. Android 앱에서 옷 추가를 진행함
4. EC2 Backend에 `POST /clothes` 요청이 성공하는지 확인함
5. Supabase `public.clothes` 테이블에 row가 생성되는지 확인함
6. AI 분석은 나중에 `ENABLE_AI_ANALYSIS=true`로 바꿔 별도 검증함

## Backend API 연동

Android 앱에서 사용하는 Backend 의류 API는 아래와 같음.

```text
POST /clothes/upload
POST /clothes
GET /clothes?user_id={uuid}
GET /clothes/{clothing_id}
PUT /clothes/{clothing_id}
DELETE /clothes/{clothing_id}
POST /clothes/{clothing_id}/analyze
```

## 검증 상태

- Android 저장/조회 코드는 `ClothingStorage` 로컬 우선, Backend 동기화 후처리 구조로 구성됨
- 팀원 테스트 기본 URL은 `http://54.180.79.90:8000` 사용함
- Android 에뮬레이터 로컬 테스트는 `http://10.0.2.2:8000` 사용함
- 실제 Android 기기 로컬 Mac 테스트는 `http://{Mac의_로컬_IP}:8000` 사용해야 함
- AI 분석 전에 로컬 item을 만들고, 서버 가능 시 pending row를 만든 뒤 성공/실패를 같은 row에 업데이트하도록 구성됨
- 현재 Codex 환경에는 Java Runtime이 없어 Gradle 빌드는 검증하지 못함

## 남은 작업

- 실제 AI 서버를 배포하고 Android 후처리 흐름과 연결해야 함
- 업로드된 이미지 기반 AI 재분석과 벡터화를 연결해야 함
- Google/Kakao/Naver OAuth 결과를 `TokenManager.saveSupabaseSession(...)`에 연결하고 refresh token 갱신을 구현해야 함
- 재분석 버튼에서 Backend `POST /clothes/{clothing_id}/analyze`를 호출하는 UI를 완성해야 함

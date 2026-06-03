# Fitcommit Android

Fitcommit Android는 Java/XML 기반 Android 앱임. 현재 흐름은 iOS와 동일하게 카테고리를 먼저 선택하고, 아이템 단독 사진을 AI 서버에 보내 분석한 뒤, 사용자가 결과를 확인/수정하고 Backend `/clothes`로 저장하는 구조임.

## 역할

- 사용자가 의류 큰 카테고리를 선택함
- 아이템 단독 사진 촬영/업로드 흐름을 제공함
- AI 서버 `/predict`에 `file`과 `category_hint`를 multipart로 전송함
- 분석 결과 화면에서 종류, 세부 종류, 대표 색상, 계절, 스타일 태그, 신뢰도를 확인/수정함
- 저장 버튼을 누르면 Backend `/clothes` 저장을 먼저 시도함
- Backend 저장 실패 시 `ClothingStorage` 로컬 저장소를 fallback으로 사용함
- 옷장 화면은 Backend 조회를 먼저 시도하고 실패하면 로컬 저장소를 사용함

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
│       │   │   └── FashionAiService.java
│       │   └── utils/
│       │       └── ClothingStorage.java
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

Android 에뮬레이터는 Mac의 `localhost`에 직접 접근할 수 없어서 `10.0.2.2`를 사용해야 함.

```text
Android Emulator -> http://10.0.2.2:8000 -> Backend FastAPI
```

개발 기본값은 `app/src/main/java/com/fitcommit/app/services/ApiClient.java`에서 관리함.

```text
BASE_URL=http://10.0.2.2:8000
BACKEND_CLOTHES_USER_ID=45482f0f-42d0-401e-a18a-77abeb0492cc
```

실제 Android 기기에서는 Mac과 같은 Wi-Fi에 연결한 뒤 Backend 주소를 Mac의 로컬 IP로 바꿔야 함.

```text
예시: http://192.168.0.10:8000
```

Mac의 로컬 IP는 아래 명령으로 확인할 수 있음.

```bash
ipconfig getifaddr en0
```

## 저장 및 조회 흐름

저장 흐름은 아래 순서로 동작함.

```text
AnalysisResultActivity
  -> ClothesBackendService.createClothing()
  -> POST /clothes 성공 시 서버 저장 완료
  -> 실패 시 ClothingStorage 로컬 저장 fallback
```

조회 흐름은 아래 순서로 동작함.

```text
Closet 화면
  -> ClothesBackendService.fetchClothes()
  -> GET /clothes?user_id=...
  -> 성공 시 Supabase 데이터 표시
  -> 실패 시 ClothingStorage 로컬 데이터 표시
```

저장 실패 시 개발 콘솔에 실패 이유를 출력하고, 사용자 화면에는 서버 저장 실패 후 기기에 임시 저장했다는 안내를 보여주는 구조임.

## 이미지 경로

Supabase Storage 업로드는 아직 Android에서 구현되지 않음. 현재는 `image_path`에 로컬 이미지 경로 또는 임시 참조값을 저장함.

향후 `clothing-images` bucket 업로드를 연결할 때는 아래 형태의 경로를 사용할 예정임.

```text
{user_id}/{main_category}/{filename}
```

## AI 서버 연동

AI 서버 `/predict` 요청은 multipart로 구성함.

```text
file=image
category_hint=top
```

허용 카테고리는 `top`, `bottom`, `outer`, `shoes`, `hat`, `bag`, `dress` 사용함.

## Backend API 연동

Android 앱에서 사용하는 Backend 의류 API는 아래와 같음.

```text
POST /clothes
GET /clothes?user_id={uuid}
GET /clothes/{clothing_id}
PUT /clothes/{clothing_id}
DELETE /clothes/{clothing_id}
```

## 검증 상태

- Android 저장/조회 코드는 Backend 우선, `ClothingStorage` fallback 구조로 구성됨
- Android 에뮬레이터 Backend URL은 `10.0.2.2:8000` 기준으로 분리됨
- 현재 Codex 환경에는 Java Runtime이 없어 Gradle 빌드는 검증하지 못함

## 남은 작업

- Android JDK 환경에서 Gradle 빌드를 검증해야 함
- Supabase Storage 이미지 업로드를 연결해야 함
- 임시 user_id 대신 Supabase Auth access token 기반 인증으로 전환해야 함

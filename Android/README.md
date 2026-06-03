# Fitcommit Android

Java + XML Layout 기반 Android 앱입니다. `Ios/MyCloset`의 SwiftUI 화면 흐름을 Android Studio에서 바로 열 수 있는 구조로 대응시켰습니다.

## 화면 대응

| iOS SwiftUI | Android |
| --- | --- |
| `LoginView` | `LoginActivity` + `activity_login.xml` |
| `MainTabView` | `MainActivity` + `activity_main.xml` |
| `HomeView` | `screen_home.xml` |
| `ClosetView` | `screen_closet.xml` |
| `AICodiLandingView` | `screen_ai_landing.xml` |
| `AIChatView` | `AIChatActivity` + `activity_ai_chat.xml` |
| `AnalysisResultView` | `AnalysisResultActivity` + `activity_analysis_result.xml` |
| `MyProfileView` | `screen_profile.xml` |

## 구조

```text
Android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/fitcommit/app/
│       │   ├── activities/
│       │   ├── adapters/
│       │   ├── models/
│       │   ├── services/
│       │   └── utils/
│       └── res/
│           ├── layout/
│           ├── drawable/
│           └── values/
├── build.gradle
├── settings.gradle
└── README.md
```

## 실행 방법

1. Android Studio에서 `Android/` 폴더를 엽니다.
2. Gradle Sync를 실행합니다.
3. `app` 실행 구성을 선택합니다.
4. 에뮬레이터 또는 실제 Android 기기에서 실행합니다.

## Backend/Supabase 의류 저장 테스트

- Android 에뮬레이터에서는 Mac의 localhost가 `10.0.2.2`로 보입니다. 개발 기본값은 `http://10.0.2.2:8000`입니다.
- 실제 Android 기기에서는 Mac과 같은 Wi-Fi에 연결한 뒤 `app/src/main/java/com/fitcommit/app/services/ApiClient.java`의 `BASE_URL`을 Mac의 LAN IP로 바꿉니다. 예: `http://192.168.0.10:8000`
- 테스트용 Supabase Auth `user_id`는 `ApiClient.BACKEND_CLOTHES_USER_ID`에 설정되어 있습니다.
- 저장은 Backend `/clothes`를 먼저 시도하고 실패하면 기존 로컬 `ClothingStorage`에 임시 저장합니다.
- 조회는 Backend `GET /clothes?user_id=...`를 먼저 사용하고 실패하면 기존 로컬 저장소와 샘플 데이터를 표시합니다.
- Supabase Storage `clothing-images` 업로드는 아직 미구현입니다. 현재는 `image_path`에 로컬 파일 경로 또는 임시 참조값을 저장합니다.

## 현재 구현 범위

- 로그인/회원가입 입력 검증
- iOS와 유사한 밝은 배경, 유리감 카드, 둥근 버튼, 하단 캡슐 탭
- 홈 대시보드, 날씨 인사이트, AI 베스트 코디, 옷장 리포트
- 옷장 화면의 공간/신체 기능 카드와 의류 그리드
- AI 코디 랜딩 화면과 패트리 채팅 더미 응답
- 분석 결과 편집/저장 화면
- 마이 페이지 설정/활동 화면

## API 연동 예정 위치

- `services/AuthService.java`: Backend `/users/signup`, `/users/login`
- `services/FashionAiService.java`: AI 서버 `/predict`
- `services/ClothesBackendService.java`: Backend `/clothes`
- `services/ChatService.java`: Backend `/chat` API

각 서비스에는 TODO 주석으로 추후 연결 지점을 남겨두었습니다.

## 참고

현재 앱은 외부 AndroidX 의존성 없이 기본 Android SDK 컴포넌트만 사용합니다. 추후 실제 제품 수준으로 확장할 때는 RecyclerView, Material Components, Room, Retrofit/OkHttp 등을 도입할 수 있습니다.

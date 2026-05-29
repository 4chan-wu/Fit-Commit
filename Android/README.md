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
- `services/ChatService.java`: Backend `/chat` API

각 서비스에는 TODO 주석으로 추후 연결 지점을 남겨두었습니다.

## 참고

현재 앱은 외부 AndroidX 의존성 없이 기본 Android SDK 컴포넌트만 사용합니다. 추후 실제 제품 수준으로 확장할 때는 RecyclerView, Material Components, Room, Retrofit/OkHttp 등을 도입할 수 있습니다.

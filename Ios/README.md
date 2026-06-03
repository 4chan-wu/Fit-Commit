# Fitcommit iOS

Fitcommit iOS는 SwiftUI 기반 앱임. 현재 1차 프로토타입에서는 전신 사진 분석이나 체형 스캔 중심 흐름이 아니라, 사용자가 의류 카테고리를 먼저 선택하고 아이템 단독 사진을 업로드하는 흐름으로 구성됨.

## 역할

- 사용자가 상의, 하의, 아우터, 신발, 모자, 가방, 원피스 중 하나를 선택함
- 옷 한 벌만 화면 중앙에 놓고 촬영/업로드하도록 안내함
- AI 서버 `/predict`에 `file`과 `category_hint`를 multipart로 전송함
- 분석 결과 확인 화면에서 종류, 세부 종류, 대표 색상, 계절, 스타일 태그, 신뢰도를 보여줌
- 사용자가 수정한 최종 값을 Backend `/clothes`로 저장함
- Backend 저장 실패 시 SwiftData 로컬 저장 fallback을 사용함
- 옷장 화면은 Backend 조회를 먼저 시도하고 실패하면 SwiftData 로컬 데이터를 사용함

## 구조

```text
Ios/
├── MyCloset.xcodeproj
├── MyCloset/
│   ├── Views/
│   │   └── AnalysisResultView.swift
│   ├── Services/
│   │   ├── AnalysisService.swift
│   │   └── BackendClothesService.swift
│   └── Models/
├── MyClosetTests/
├── MyClosetUITests/
└── README.md
```

## 실행 방법

Xcode 15 이상에서 `Ios/MyCloset.xcodeproj`를 열고 시뮬레이터 또는 실제 기기에서 실행함.

명령어로 빌드 확인이 필요하면 아래처럼 실행함.

```bash
xcodebuild \
  -project Ios/MyCloset.xcodeproj \
  -scheme MyCloset \
  -configuration Debug \
  -destination generic/platform=iOS \
  CODE_SIGNING_ALLOWED=NO \
  build
```

현재 코드 기준으로 iOS Debug 빌드는 성공 확인됨.

## Backend 설정

iOS 앱은 아래 설정이 있으면 Backend `/clothes` 저장을 우선 시도함.

```text
FITCOMMIT_BACKEND_BASE_URL
FITCOMMIT_SUPABASE_USER_ID
```

개발 기본값은 아래와 같음.

```text
FITCOMMIT_BACKEND_BASE_URL=http://127.0.0.1:8000
FITCOMMIT_SUPABASE_USER_ID=45482f0f-42d0-401e-a18a-77abeb0492cc
```

`BackendClothesService.swift`는 환경변수 또는 Info.plist 값을 읽고, 값이 없으면 위 개발 기본값을 사용함.

## 시뮬레이터와 실기기 URL 차이

iOS 시뮬레이터는 Mac에서 실행 중인 Backend에 아래 주소로 접근 가능함.

```text
http://127.0.0.1:8000
```

실제 iPhone은 자기 자신의 localhost를 보기 때문에 `127.0.0.1`로 Mac 서버에 접근할 수 없음. 실제 기기 테스트 시에는 Mac과 같은 Wi-Fi에 연결하고 Mac의 로컬 IP를 사용해야 함.

```text
예시: http://192.168.0.10:8000
```

Mac의 로컬 IP는 아래 명령으로 확인할 수 있음.

```bash
ipconfig getifaddr en0
```

## 저장 및 조회 흐름

저장 흐름은 아래처럼 동작함.

```text
AnalysisResultView
  -> BackendClothesService.createClothing()
  -> POST /clothes 성공 시 Supabase 저장 완료
  -> 실패 시 SwiftData 로컬 저장 fallback
```

조회 흐름은 아래처럼 동작함.

```text
Closet 화면
  -> BackendClothesService.fetchClothes()
  -> GET /clothes?user_id=...
  -> 성공 시 Supabase 데이터 표시
  -> 실패 시 SwiftData 로컬 데이터 표시
```

저장 실패 시 콘솔에 실패 이유를 출력하고, 사용자에게 서버 저장 실패 후 기기에 임시 저장했다는 안내를 보여주는 구조임.

## 이미지 경로

Supabase Storage 업로드는 아직 iOS에서 구현되지 않음. 현재는 `image_url` 또는 `image_path`에 로컬 이미지 경로나 임시 참조값을 저장함.

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

허용 카테고리는 아래 값만 사용함.

```text
top
bottom
outer
shoes
hat
bag
dress
```

AI 분석 결과는 바로 저장하지 않고, `AnalysisResultView`에서 사용자가 확인하고 수정한 뒤 Backend로 저장함.

## 검증 상태

- iOS Debug 빌드 성공 확인됨
- Backend 저장 우선, SwiftData fallback 구조로 구성됨
- iOS 시뮬레이터 개발 기본 URL은 `http://127.0.0.1:8000` 사용함
- 실제 iPhone 테스트는 Mac 로컬 IP 설정이 필요함

## 남은 작업

- Supabase Storage 이미지 업로드를 연결해야 함
- 임시 `FITCOMMIT_SUPABASE_USER_ID` 방식에서 Supabase Auth access token 기반 인증으로 전환해야 함
- 실제 기기에서 카메라, 네트워크, 저장/조회 end-to-end를 추가 검증해야 함

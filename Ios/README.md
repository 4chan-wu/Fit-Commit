# Fitcommit iOS

> 2026-10-14부터 AWS/FastAPI 요청을 사용하지 않으며 Supabase Auth, PostgREST, Storage를 직접 사용함.

Fitcommit iOS는 SwiftUI 기반 앱임. 현재 1차 프로토타입에서는 전신 사진 분석이나 체형 스캔 중심 흐름이 아니라, 사용자가 의류 카테고리를 먼저 선택하고 아이템 단독 사진을 업로드하는 흐름으로 구성됨.

## 역할

- 사용자가 상의, 하의, 아우터, 신발, 모자, 가방, 원피스 중 하나를 선택함
- 옷 한 벌만 화면 중앙에 놓고 촬영/업로드하도록 안내함
- AI 서버 호출 전에 SwiftData 로컬 옷장에 먼저 저장함
- 로컬 저장 후 Backend `/clothes` 동기화를 시도함
- `ENABLE_AI_ANALYSIS=false`이면 AI 서버 `/predict`를 호출하지 않음
- `ENABLE_AI_ANALYSIS=true`일 때만 AI 서버 `/predict`에 `file`과 `category_hint`를 multipart로 전송함
- AI 성공 시 로컬 데이터를 `completed`로 업데이트하고, remote id가 있으면 같은 row를 업데이트함
- AI 실패 시 로컬 데이터를 `failed`로 유지하고, remote id가 있으면 `analysis_error`를 저장함
- 분석 결과 확인 화면에서 종류, 세부 종류, 대표 색상, 계절, 스타일 태그, 신뢰도를 보여줌
- 사용자가 수정한 최종 값을 SwiftData에 먼저 반영하고, remote id가 있으면 Backend `PUT /clothes/{clothing_id}`로 다시 저장함
- AI 서버 없이도 사용자가 카테고리, 세부 종류, 대표 색상, 계절을 직접 수정할 수 있음
- 대표색은 iOS 로컬 이미지 처리로 추천값을 계산하고, 사용자가 직접 바꿀 수 있음
- 수동 수정은 `analysis_method=manual`로 저장하고 `analysis_status=pending`을 유지할 수 있음
- 배경 제거는 저장 필수 조건이 아니며 선택 기능으로 분리함
- 원본 이미지는 항상 먼저 저장하고, 배경 제거 실패 시에도 원본 이미지와 clothes row는 유지함
- iOS는 `VisionImageService`의 `VNGenerateForegroundInstanceMaskRequest` 기반 배경 제거를 상세 화면에서 다시 시도할 수 있게 구성함
- 배경 제거 성공 시 PNG를 `background_removed/` Storage 경로에 업로드하고 `background_removal_status=completed`로 저장함
- 배경 제거 실패 시 `background_removal_status=failed`, `background_removal_error`를 저장함
- Backend 저장 실패 시에도 SwiftData 로컬 item은 유지됨
- 옷장 화면은 SwiftData 로컬 데이터와 Backend 조회 결과를 remote id/local id 기준으로 병합함
- 3D 공간 스캔과 신체 비율 측정 진입점은 옷장 화면에 계속 유지함

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

## Supabase 설정

iOS Scheme 환경변수 또는 Info.plist에 공개 가능한 Supabase 설정을 추가함.

```text
FITCOMMIT_SUPABASE_URL=https://iuplvspexcgvhonqgfag.supabase.co
FITCOMMIT_SUPABASE_ANON_KEY=Supabase의_공개키
FITCOMMIT_SUPABASE_CLOTHING_BUCKET=clothing-images
```

`service_role` 키는 절대 앱에 넣지 않음. 로그인 후 받은 access token과 RLS로 사용자별 데이터 접근을 제한함.

## 사용자별 옷장 구조

iOS 앱은 현재 `wardrobe_id`를 몰라도 옷 등록이 가능함. Backend가 `POST /clothes` 저장 시 사용자 기본 옷장을 자동 생성 또는 조회해서 `clothes.wardrobe_id`에 연결함.

- 사용자는 여러 옷장을 가질 수 있음
- 기본 옷장은 사용자당 1개만 가능함
- 추가 옷장은 여러 개 가능함
- 같은 사용자 안에서 같은 옷장 이름은 중복될 수 없음
- 향후 옷장 선택 UI는 Backend `/wardrobes` API를 사용해야 함
- `BackendClothesService.swift`에는 기본 옷장 조회용 `fetchDefaultWardrobe()` 구조를 준비함
- 웹도 같은 Backend wardrobe API를 사용해야 함

## 시뮬레이터와 실기기 URL 차이

iOS 시뮬레이터는 Mac에서 실행 중인 Backend에 아래 주소로 접근 가능함.

```text
http://127.0.0.1:8000
```

실제 iPhone은 자기 자신의 localhost를 보기 때문에 `127.0.0.1`로 Mac 서버에 접근할 수 없음. 실제 기기 테스트 시에는 Mac과 같은 Wi-Fi에 연결하고 Mac의 로컬 IP를 사용해야 함.

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

저장 흐름은 아래처럼 동작함. AI 분석 성공과 서버 저장 성공은 로컬 저장의 필수 조건이 아님.

```text
ClothesUploadView
  -> SwiftData에 local item 먼저 저장
  -> Backend POST /clothes/upload로 Storage 업로드 시도
  -> 업로드 성공 시 image_path/image_url을 서버 row에 사용
  -> 업로드 실패 시 local:// fallback 사용
  -> Backend POST /clothes 동기화 시도
  -> 성공 시 remote_id 저장 + sync_status=synced
  -> 실패 시 로컬 유지 + sync_status=sync_failed
  -> ENABLE_AI_ANALYSIS=false이면 AI 서버 호출 건너뜀
  -> ENABLE_AI_ANALYSIS=true이면 AI 서버 /predict 호출
  -> 성공 시 로컬 completed 업데이트 + remote id가 있으면 PUT /clothes/{id}
  -> 실패 시 로컬 failed 업데이트 + remote id가 있으면 PUT /clothes/{id}
  -> AnalysisResultView에서 사용자 확정값을 로컬/서버에 업데이트
```

조회 흐름은 아래처럼 동작함.

```text
Closet 화면
  -> SwiftData 로컬 데이터 조회
  -> BackendClothesService.fetchClothes()
  -> GET /clothes?user_id=...
  -> 성공 시 Supabase 데이터와 로컬 데이터를 중복 없이 병합
  -> 실패 시 SwiftData 로컬 데이터만 표시
```

AI 실패 item과 서버 동기화 실패 item도 옷장 화면에 표시됨. 실패 item은 “분석 실패 / 다시 분석 가능”, 서버 실패 item은 “서버 동기화 실패” 상태로 표시됨.

## 이미지 경로

iOS는 사진을 SwiftData에 먼저 저장한 뒤 Backend `POST /clothes/upload`로 Supabase Storage 업로드를 시도함. 업로드 성공 시 `/clothes` 생성 payload에 `image_path`와 `image_url`을 포함함.

Storage 경로는 아래 형태를 사용함.

```text
{user_id}/{main_category}/{filename}.jpg
```

업로드 실패 시에도 옷 등록은 계속 진행함. 이때 `local://...` 또는 앱 내부 로컬 참조를 fallback으로 저장함. `local://` 경로는 Supabase Dashboard에서 볼 수 없는 앱 내부 참조임.

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

AI 분석 결과는 기존 pending row에 먼저 업데이트되고, `AnalysisResultView`에서 사용자가 확인하고 수정한 최종값을 다시 Backend로 저장함.

## 옷장 관리

iOS 옷장 화면은 Backend wardrobes API를 사용해 사용자 옷장 목록을 불러옴.

- 옷장 화면에서 “전체”, 기본 옷장, 추가 옷장 필터를 표시함
- 특정 옷장을 선택하면 Backend `GET /clothes?user_id=...&wardrobe_id=...`로 해당 옷장 옷만 조회함
- “옷장 관리” 화면에서 옷장 생성, 이름/설명 수정, 추가 옷장 삭제를 지원함
- 기본 옷장은 “기본” 배지로 표시함
- 기본 옷장은 삭제할 수 없음
- 추가 옷장 삭제 시 Backend가 해당 옷장의 옷을 기본 옷장으로 이동함
- 옷 등록 화면에서 저장할 옷장을 Picker로 선택할 수 있음
- 옷장 목록을 불러오지 못하면 `wardrobe_id` 없이 저장하고 Backend가 기본 옷장을 자동 연결함
- 의류 상세/수정 화면에서도 옷장을 변경할 수 있음
- 기존 3D 공간 스캔, 신체 비율 측정, 대표색 선택, 계절 선택, 배경 제거 기능은 유지함
- 현재는 테스트 user_id를 사용함
- 실제 서비스에서는 Supabase Auth access token을 Backend에 보내고 Backend가 user_id를 결정해야 함
AI 서버가 꺼져 있어도 로컬 옷장에는 pending/failed item이 남아야 함.
현재 Supabase 저장 테스트 단계에서는 `ENABLE_AI_ANALYSIS=false`로 두고 AI 서버를 실행하지 않아도 됨.

## Supabase 저장 테스트 순서

1. EC2 Backend `http://54.180.79.90:8000`을 사용함
2. AI 서버는 실행하지 않아도 됨
3. iOS 시뮬레이터에서 옷 추가를 진행함
4. EC2 Backend에 `POST /clothes` 요청이 성공하는지 확인함
5. Supabase `public.clothes` 테이블에 row가 생성되는지 확인함
6. AI 분석은 나중에 `ENABLE_AI_ANALYSIS=true`로 바꿔 별도 검증함

## 검증 상태

- iOS Debug 빌드 성공 확인됨
- SwiftData 로컬 우선, Backend 동기화 후처리 구조로 구성됨
- 팀원 테스트 기본 URL은 `http://54.180.79.90:8000` 사용함
- iOS 시뮬레이터 로컬 테스트는 `http://127.0.0.1:8000` 사용 가능함
- 실제 iPhone 로컬 Mac 테스트는 `http://{Mac의_로컬_IP}:8000` 사용해야 함

## 남은 작업

- 실제 AI 서버를 배포하고 iOS 후처리 흐름과 연결해야 함
- 업로드된 이미지 기반 AI 재분석과 벡터화를 연결해야 함
- 임시 `FITCOMMIT_SUPABASE_USER_ID` 방식에서 Supabase Auth access token 기반 인증으로 전환해야 함
- 실제 기기에서 카메라, 네트워크, 저장/조회 end-to-end를 추가 검증해야 함
- 재분석 버튼에서 Backend `POST /clothes/{clothing_id}/analyze`를 호출하는 UI를 완성해야 함

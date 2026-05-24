# FitCommit Fashion AI Server

FastAPI 기반 AI 의류 분석 서버입니다. 사용자가 업로드한 의류 이미지를 실시간으로 분석하여 카테고리, 색상, 시즌 정보를 제공합니다.

---

## 📖 프로젝트 개요

**목표**: iOS 클라이언트가 의류 이미지를 업로드하면, AI 모델이 다음을 자동으로 분석합니다:
- **의류 카테고리**: T-shirt, Shirt, Hoodie, Sweater, Pants, Skirt, Dress, Outerwear
- **지배적 색상**: HEX 색상코드 (예: `#1A237E`)
- **시즌 분류**: Spring/Summer, Fall/Winter 등
- **신뢰도 점수**: 각 분석의 정확도 (0~1)

**사용 사례**:
- 사용자 옷장(Closet) 자동화 관리
- 착장 추천 시스템의 기초 데이터 제공
- 색상/시즌 기반 룩 필터링

---

## 🛠️ 환경 설정 (Prerequisites)

### 필수 요구사항

| 항목 | 버전 | 비고 |
|------|------|------|
| **Python** | 3.10+ | 3.11 권장 |
| **pip** | 최신 | `pip install --upgrade pip` |
| **CUDA** | 11.8+ (선택) | GPU 가속 사용 시 (권장) |

### 설치 단계

#### 1. Python 설치 확인
```bash
python --version
python -m venv --help  # venv 모듈 확인
```

#### 2. 가상환경 생성 및 활성화
```bash
# 프로젝트 루트에서
python -m venv venv

# macOS / Linux
source venv/bin/activate

# Windows
venv\Scripts\activate
```

#### 3. 의존성 설치
```bash
pip install --upgrade pip setuptools wheel
pip install -r requirements.txt
```

**설치 시간**: 약 3~5분 (PyTorch 다운로드 포함)

---

## 🚀 실행 방법

### 개발 서버 시작
```bash
# 가상환경 활성화 (위의 2단계 참고)

# 개발 모드 (자동 리로드)
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### 프로덕션 서버 시작
```bash
# 워커 4개로 동시 요청 처리
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 4
```

### 서버 상태 확인
```bash
curl http://localhost:8000/health
# 응답: {"status": "ok"}
```

### 문서 접근
- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

---

## 📡 API 명세

### 1. 헬스 체크

**엔드포인트**: `GET /health`

**설명**: 서버 및 모델 로드 상태 확인

**응답**:
```json
{
  "status": "ok"
}
```

---

### 2. 의류 분석 예측

**엔드포인트**: `POST /predict`, `POST /predict/`

**설명**: 업로드된 이미지 파일을 분석하여 의류 카테고리, 색상, 시즌 정보 반환

**요청**:
- **Content-Type**: `multipart/form-data`
- **파라미터**:
  | 이름 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | `file` | File | O | 의류 이미지 (JPG, PNG, WEBP) |

**요청 예시** (cURL):
```bash
curl -X POST "http://localhost:8000/predict" \
  -F "file=@/path/to/tshirt.jpg"
```

**응답** (성공 200):
```json
{
  "status": "success",
  "analysis": {
    "category": "T-shirt",
    "confidence_cat": 0.92,
    "color": "#1A237E",
    "season": "Spring/Summer",
    "confidence_sea": 0.78,
    "method_sea": "heuristic"
  }
}
```

**응답** (실패 예시):

| 상태코드 | 오류 | 설명 |
|---------|------|------|
| 400 | `BAD_REQUEST` | 빈 이미지 파일 업로드 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 이미지가 아닌 파일 업로드 |
| 500 | `INTERNAL_SERVER_ERROR` | 모델 예측 실패 |

**응답 필드 설명**:
- `category`: 감지된 의류 카테고리
- `confidence_cat`: 카테고리 예측 신뢰도 (0~1)
- `color`: 지배적 색상 (HEX 형식)
- `season`: 의류에 어울리는 시즌
- `confidence_sea`: 시즌 분류 신뢰도 (0~1)
- `method_sea`: 시즌 분류 방식 (현재: heuristic)

---

## 💾 데이터 관리

### 모델 파일 위치

```
fashion-ai-server/
├── models/
│   ├── clothing_mobilenet_v3_small.pth   # 의류 분류 모델 (~10MB)
│   └── season_classifier.pth              # 시즌 분류 모델 (선택사항)
└── ...
```

**모델 로드 방식**:
- 환경 변수 `CLOTHING_MODEL_PATH` 설정 시 해당 경로 사용
- 미설정 시 기본 경로: `./models/clothing_mobilenet_v3_small.pth`

```bash
# 커스텀 모델 경로 지정 예시
export CLOTHING_MODEL_PATH="/path/to/custom_model.pth"
uvicorn main:app --reload
```

### 데이터 전처리 흐름

#### 1. 이미지 입력
- 지원 형식: JPG, PNG, WEBP
- 권장 크기: 224×224 px (자동 리사이징)
- 최대 파일 크기: 제한 없음 (필요 시 설정 가능)

#### 2. 의류 분류 (`classification_service.py`)
```
이미지 수신
  ↓
PIL로 이미지 로드
  ↓
224×224 리사이징
  ↓
정규화 (ImageNet 기준)
  ↓
MobileNet V3 모델 추론
  ↓
카테고리 + 신뢰도 반환
```

**모델 정보**:
- **기반 아키텍처**: MobileNet V3 Small (경량, 빠른 추론)
- **사전 학습 데이터**: ImageNet
- **추론 시간**: CPU ~200ms, GPU ~50ms

#### 3. 색상 추출 (`color_service.py`)
```
이미지 수신
  ↓
중앙 영역 추출 (관심 영역)
  ↓
K-Means 클러스터링 (k=5)
  ↓
지배적 색상 선택
  ↓
HEX 코드 변환 (#RRGGBB)
```

#### 4. 시즌 분류 (`season_service.py`)
```
의류 카테고리 + 색상
  ↓
휴리스틱 규칙 적용
  ↓
시즌 라벨 결정
  ↓
신뢰도 점수 계산
```

### 캐싱 & 최적화

- **모델 캐싱**: 서버 시작 시 메모리에 로드 (한 번만)
- **비동기 처리**: 3개 분석 작업(분류, 색상, 시즌)을 병렬 실행
- **배치 처리 미지원**: 현재는 단일 이미지 처리만 지원

---

##  개발 및 개선 로드맵

### 현재 상태 (MVP)
-  👍 MobileNet V3 기반 기본 분류
-  👍 K-Means 색상 추출
-  👍 휴리스틱 시즌 분류
-  👍 FastAPI 기반 REST API

### 예정된 개선 사항
- [ ] 의류 데이터셋으로 Fine-tuning (정확도 향상)
- [ ] 배치 예측 API (`POST /predict/batch`)
- [ ] 이미지 검증 및 품질 체크
- [ ] 모델 버전 관리 시스템
- [ ] 예측 결과 로깅 및 모니터링
- [ ] 모바일 최적화 모델 경량화

### Fine-tuning 예정 카테고리
```
상의: T-shirt, Shirt, Polo, Hoodie, Sweater, Blouse
하의: Pants, Jeans, Shorts, Skirt, Leggings
아우터: Jacket, Coat, Blazer, Vest
드레스: Dress, One-piece
기타: Hat, Scarf, Accessories
```

---

## 해결해볼 문제점

### 문제 1: CUDA/GPU 인식 안 됨
```bash
# GPU 확인
python -c "import torch; print(torch.cuda.is_available())"

# CPU로 강제 실행
export TORCH_DEVICE=cpu
uvicorn main:app --reload
```

### 문제 2: 메모리 부족 (OOM)
```bash
# 더 가벼운 모델 사용 (현재 이미 MobileNet V3 Small)
# 또는 워커 수 감소
uvicorn main:app --workers 1
```

### 문제 3: 이미지 파일 업로드 실패
```bash
# 파일 형식 확인
file /path/to/image.jpg

# 지원되는 MIME 타입:
# - image/jpeg
# - image/png
# - image/webp
```

---

##  의존성 상세 정보

의존성 설명은 [requirements.txt](./requirements.txt)의 주석을 참고하세요.

**핵심 스택**:
- **Web**: FastAPI, Uvicorn
- **ML**: PyTorch, TorchVision
- **Image Processing**: PIL, OpenCV
- **Data**: NumPy, scikit-learn

---

##  기술 문의

Chan • Organization: Sahmyook University • profile: https://bit.ly/HeechanWooBusinessCard

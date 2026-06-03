# Fitcommit AI

Fitcommit AI는 의류 아이템 사진을 분석하는 FastAPI 서버임. 1차 프로토타입에서는 전신 사진에서 상의/하의를 자동 분리하지 않고, 앱에서 사용자가 먼저 큰 카테고리를 선택한 뒤 아이템 단독 사진을 업로드하는 흐름을 사용함.

## 역할

- 앱에서 받은 `file`과 `category_hint`를 분석함
- category별 텍스트 후보군과 이미지 사이의 CLIP similarity를 계산함
- 가장 점수가 높은 후보를 `subcategory`로 반환함
- 대표 색상과 시즌, 스타일 태그, confidence를 함께 반환함
- CLIP 로딩 또는 추론 실패 시 기존 MobileNetV3 fallback을 사용함

## 구조

```text
AI/
├── main.py
├── services/
│   ├── fashion_clip_service.py
│   ├── classification_service.py
│   ├── color_service.py
│   └── season_service.py
├── training/
├── requirements.txt
└── README.md
```

`services/fashion_clip_service.py`가 현재 세부 카테고리 분석의 중심임. `classification_service.py`의 MobileNetV3 흐름은 삭제하지 않고 fallback 용도로 유지함.

## 현재 CLIP 구조

- transformers 기반 `CLIPModel`, `CLIPProcessor`를 사용함
- 기본 모델은 `openai/clip-vit-base-patch32` 사용함
- `FASHION_CLIP_MODEL_ID` 환경변수로 FashionCLIP 계열 Hugging Face 모델로 교체할 수 있음
- 모델은 lazy singleton 구조로 최초 1회만 로딩함
- GPU가 있으면 `cuda`, 없으면 `cpu`로 실행함
- 후보군 score에 softmax를 적용해 confidence를 계산함
- 실제 CLIP 사용 시 `method`는 `"clip"`으로 반환함
- fallback 사용 시 `method`는 `"fallback"`으로 반환함

FashionCLIP으로 바꾸려면 실행 전에 모델 ID를 지정함.

```bash
export FASHION_CLIP_MODEL_ID="your-fashionclip-model-id"
```

## `/predict` API

`POST /predict`는 `multipart/form-data`로 요청함.

```text
file=image
category_hint=top
```

허용되는 `category_hint`는 아래 값만 사용함.

```text
top
bottom
outer
shoes
hat
bag
dress
```

카테고리별 텍스트 후보군은 `TEXT_CANDIDATES`에서 관리함.

- `top`: `t-shirt`, `shirt`, `hoodie`, `sweatshirt`, `knit`, `blouse` 사용함
- `bottom`: `jeans`, `slacks`, `shorts`, `skirt`, `cargo pants` 사용함
- `outer`: `jacket`, `coat`, `blazer`, `padding jacket`, `windbreaker` 사용함
- `shoes`: `sneakers`, `loafers`, `boots`, `sandals` 사용함
- `hat`: `cap`, `beanie`, `bucket hat` 사용함
- `bag`: `backpack`, `tote bag`, `shoulder bag`, `cross bag` 사용함
- `dress`: `dress`, `one-piece`, `long dress` 사용함

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

앱에서는 이 결과를 바로 저장하지 않고 사용자가 확인/수정한 뒤 Backend `/clothes`로 저장함.

## 실행 방법

Python 3.11 사용을 권장함. 첫 실행 시 CLIP 모델 다운로드 때문에 시간이 걸릴 수 있음.

```bash
cd AI
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8001
```

상태 확인은 아래처럼 함.

```bash
curl http://127.0.0.1:8001/health
```

`/predict` 테스트는 아래처럼 함.

```bash
curl -X POST http://127.0.0.1:8001/predict \
  -F "file=@/path/to/clothing.jpg" \
  -F "category_hint=top"
```

응답의 `analysis.method`가 `"clip"`이면 실제 CLIP 이미지-텍스트 similarity가 사용된 상태임. `"fallback"`이면 CLIP 로딩 또는 추론 실패로 MobileNetV3 fallback이 사용된 상태임.

## requirements

CLIP 추론에 필요한 주요 패키지는 아래와 같음.

```text
torch
transformers
pillow
numpy
```

OpenCV/KMeans 기반 대표 색상 추출을 위해 기존 이미지 처리 패키지도 계속 사용함.

## 학습/전처리 영역

`training/`은 현재 추론 서버와 분리된 실험/전처리 영역임. 예전 YOLO 데이터셋 전처리 흔적은 남아 있지만, 1차 프로토타입 앱 흐름에서는 YOLO 기반 전신 사진 자동 분리를 사용하지 않음.

향후 고도화 단계에서 전신 사진 자동 감지나 객체 탐지가 필요해지면 별도 기능으로 다시 검토함.

## 검증 상태

- AI 서버 Python 문법 검사 성공함
- `fashion_clip_service.py` import 확인 성공함
- 기본 CLIP 설정은 `openai/clip-vit-base-patch32`와 CPU 환경으로 확인됨
- 실제 모델 다운로드와 추론은 실행 환경의 네트워크와 Hugging Face 캐시에 따라 달라질 수 있음

## 남은 작업

- Fitcommit 데이터에 맞는 FashionCLIP 모델 후보를 정하고 성능을 비교해야 함
- style tag와 season 규칙을 실제 앱 데이터 기준으로 보정해야 함
- 이미지 품질이 낮은 경우의 오류 메시지와 fallback 기준을 더 세밀하게 만들어야 함

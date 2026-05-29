# Fitcommit AI

의류 이미지 분석을 담당하는 FastAPI 서버와 학습/전처리 코드를 함께 보관하는 영역입니다.

## 구조

```text
AI/
├── main.py
├── services/
│   ├── classification_service.py
│   ├── color_service.py
│   └── season_service.py
├── training/
│   ├── src/
│   │   ├── preprocess.py
│   │   └── main.ipynb
│   └── models/
│       └── configs/data.yaml
├── requirements.txt
└── README.md
```

## 추론 서버

`main.py`와 `services/`는 iOS 앱에서 호출하는 fashion-ai-engine 서버입니다.

- `GET /health`: 서버 상태 확인
- `POST /predict`: 이미지 파일을 받아 의류 카테고리, 대표 색상, 시즌 결과 반환

응답 예시:

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

## 실행

```bash
cd AI
python -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

확인:

```bash
curl http://localhost:8000/health
```

## 환경변수

| 변수 | 필수 | 설명 | 기본값 |
| --- | --- | --- | --- |
| `CLOTHING_MODEL_PATH` | 선택 | 의류 카테고리 모델 가중치 경로 | `AI/models/clothing_mobilenet_v3_small.pth` |
| `SEASON_MODEL_PATH` | 선택 | 시즌 분류 모델 가중치 경로 | `AI/models/season_mobilenet_v3_small.pth` |

모델 가중치가 없으면 서버는 가능한 범위에서 fallback 동작을 합니다. 시즌 분류는 모델이 없을 때 이미지 밝기/채도 기반 휴리스틱을 사용합니다.

## 학습/전처리

`training/`은 추론 서버와 분리된 학습/전처리 영역입니다.

- `training/src/preprocess.py`: 원천 이미지와 JSON 라벨을 YOLO 형식 데이터셋으로 변환
- `training/models/configs/data.yaml`: YOLO 데이터셋 설정
- `training/src/main.ipynb`: Colab 실험 노트북

전처리 실행 예시:

```bash
cd AI/training/src
python preprocess.py
```

전처리 스크립트는 `AI/training/data/raw/images`, `AI/training/data/raw/labels` 아래에 원천 데이터가 있다고 가정합니다. 생성된 데이터셋과 모델 가중치는 Git에 올리지 않고 별도 스토리지에서 관리합니다.

# Clothing Classifier 학습 가이드

이 문서는 MobileNetV3-Small 기반 8-class 의류 분류 모델을 학습하는 절차을 설명합니다.

## Dataset 폴더 구조

학습 스크립트는 `torchvision.datasets.ImageFolder` 기반이며, 데이터는 아래 구조여야 합니다.

```text
fitcommit_clothing_dataset/
  train/
    T-shirt/
    Shirt/
    Hoodie/
    Sweater/
    Pants/
    Skirt/
    Dress/
    Outerwear/
  val/
    T-shirt/
    Shirt/
    Hoodie/
    Sweater/
    Pants/
    Skirt/
    Dress/
    Outerwear/
  test/
    T-shirt/
    Shirt/
    Hoodie/
    Sweater/
    Pants/
    Skirt/
    Dress/
    Outerwear/
```

`train`과 `val`은 필수입니다. `test`는 선택이지만, 있으면 학습 종료 후 test accuracy와 confusion matrix를 출력합니다.

## Colab에서 학습하는 법

1. Google Drive에 데이터셋을 업로드합니다.

```text
/content/drive/MyDrive/fitcommit_clothing_dataset
```

2. `AI/notebooks/train_clothing_mobilenet_v3_colab.ipynb`를 Colab 또는 VS Code Colab extension에서 엽니다.
3. GPU 확인 셀을 실행해 CUDA가 켜져 있는지 확인합니다.
4. Google Drive mount 셀을 실행합니다.
5. 프로젝트가 Colab 런타임에 없다면 노트북의 `git clone` 셀에서 저장소 URL을 수정한 뒤 실행합니다.
6. 학습 셀을 실행합니다.

학습 스크립트를 직접 실행하려면 프로젝트 루트에서 아래처럼 실행할 수 있습니다.

```bash
python AI/training/train_clothing_mobilenet_v3.py \
  --data-dir /content/drive/MyDrive/fitcommit_clothing_dataset \
  --output /content/drive/MyDrive/clothing_mobilenet_v3_small.pth \
  --epochs-head 5 \
  --epochs-fine 15 \
  --batch-size 32 \
  --lr-head 0.001 \
  --lr-fine 0.0001 \
  --num-workers 2 \
  --seed 42
```

학습은 두 단계로 진행됩니다.

1. MobileNetV3 feature extractor를 freeze하고 classifier head만 학습합니다.
2. 전체 모델을 unfreeze하여 fine-tuning합니다.

validation accuracy가 가장 높은 checkpoint가 저장됩니다.

## 학습된 pth 파일을 프로젝트에 넣는 법

학습된 모델 파일은 아래 경로에 저장합니다.

```text
AI/models/clothing_mobilenet_v3_small.pth
```

Colab 노트북의 마지막 셀은 Google Drive에 저장된 `clothing_mobilenet_v3_small.pth`를 프로젝트의 `AI/models` 폴더로 복사합니다. 로컬에서 학습했다면 아래처럼 출력 경로를 바로 지정해도 됩니다.

```bash
python AI/training/train_clothing_mobilenet_v3.py \
  --data-dir ./dataset \
  --output AI/models/clothing_mobilenet_v3_small.pth
```

대용량 dataset과 `.pth` 모델 파일은 git에 추가하지 마세요.

## Label 순서가 중요한 이유

모델의 마지막 classifier는 index `0..7`을 예측합니다. 서비스는 이 index를 아래 순서의 카테고리로 해석합니다.

```text
0 T-shirt
1 Shirt
2 Hoodie
3 Sweater
4 Pants
5 Skirt
6 Dress
7 Outerwear
```

학습할 때의 label 순서와 추론할 때의 label 순서가 다르면, 모델이 `Dress`를 맞혀도 서비스가 다른 카테고리로 반환할 수 있습니다. 그래서 학습 checkpoint에는 `categories`가 함께 저장되며, FastAPI 서비스는 checkpoint의 `categories`와 코드의 `CLOTHING_CATEGORIES`가 다르면 실행 중 명확한 에러를 냅니다.

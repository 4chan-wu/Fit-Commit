import os
import json
import cv2
import random
import numpy as np
from tqdm import tqdm

# --- [설정 1] 코랩/로컬 환경 자동 감지 및 경로 설정 ---
# 스크립트가 실행되는 위치를 기준으로 상대 경로를 설정
if 'google.colab' in str(get_ipython() if 'get_ipython' in globals() else ''):
    # 코랩 환경일 경우 (드라이브 마운트 전제)
    PROJECT_ROOT = '/content/drive/MyDrive/FitCommit-ClothAi'
    print("코랩 환경에서 실행 중. 드라이브 경로 사용.")
else:
    # 로컬 VS 코드 환경일 경우 (G: 드라이브 기준)
    # 현재 파일이 src 폴더 안에 있다고 가정하므로 한 단계 위로 설정
    PROJECT_ROOT = os.path.abspath(os.path.join(os.getcwd(), ".."))
    print(f"로컬 환경에서 실행 중. 기준 경로: {PROJECT_ROOT}")

# --- [설정 2] 세부 경로 (os.path.join으로 호환성 확보) ---
RAW_IMG_DIR = os.path.join(PROJECT_ROOT, 'data', 'raw', 'images')
RAW_JSON_DIR = os.path.join(PROJECT_ROOT, 'data', 'raw', 'labels')
BASE_SAVE_DIR = os.path.join(PROJECT_ROOT, 'data', 'dataset')

# --- [설정 3] 하이퍼파라미터 ---
LIMIT = 1500  
TRAIN_RATIO = 0.8 
TARGET_SIZE = (640, 640) 
CLASS_ID = 0  # 0: 블라우스 (나중에 티셔츠 등 추가 시 변경)

def letterbox_and_convert(img, bbox, target_size=(640, 640)):
    """이미지 비율 유지 리사이즈 및 YOLO 포맷 변환"""
    ih, iw = img.shape[:2]
    tw, th = target_size
    
    scale = min(tw / iw, th / ih)
    nw, nh = int(iw * scale), int(ih * scale)
    
    resized_img = cv2.resize(img, (nw, nh), interpolation=cv2.INTER_LINEAR)
    
    # 캔버스 생성 (회색 배경)
    new_img = np.full((th, tw, 3), 114, dtype=np.uint8)
    new_img[:nh, :nw, :] = resized_img
    
    # 좌표 보정 (xmin, ymin, xmax, ymax -> x_center, y_center, w, h)
    xmin, ymin, xmax, ymax = bbox
    dw = 1. / tw
    dh = 1. / th
    
    x_center = ((xmin + xmax) * scale / 2.0) * dw
    y_center = ((ymin + ymax) * scale / 2.0) * dh
    w = ((xmax - xmin) * scale) * dw
    h = ((ymax - ymin) * scale) * dh
    
    return new_img, (np.clip(x_center, 0, 1), np.clip(y_center, 0, 1), 
                    np.clip(w, 0, 1), np.clip(h, 0, 1))

def setup_dirs():
    """저장할 폴더 구조 생성"""
    for split in ['train', 'val']:
        os.makedirs(os.path.join(BASE_SAVE_DIR, split, 'images'), exist_ok=True)
        os.makedirs(os.path.join(BASE_SAVE_DIR, split, 'labels'), exist_ok=True)

def process():
    setup_dirs()
    
    # 지원하는 이미지 확장자 리스트
    valid_extensions = ('.jpg', '.jpeg', '.png', '.JPG', '.PNG')
    img_files = [f for f in os.listdir(RAW_IMG_DIR) if f.endswith(valid_extensions)]
    
    random.seed(42)
    random.shuffle(img_files)
    
    valid_count = 0
    for filename in tqdm(img_files, desc=f"Processing Class {CLASS_ID}"):
        if valid_count >= LIMIT: break
        
        name = os.path.splitext(filename)[0]
        json_path = os.path.join(RAW_JSON_DIR, name + '.json')
        
        if os.path.exists(json_path):
            img = cv2.imread(os.path.join(RAW_IMG_DIR, filename))
            if img is None: continue
            
            try:
                with open(json_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # [중요] JSON 구조에 따라 '옷의_위치_정보' 키가 맞는지 확인 필요!
                bbox = data['옷의_위치_정보'] 
                
                final_img, yolo_bbox = letterbox_and_convert(img, bbox, TARGET_SIZE)
                
                split = 'train' if random.random() < TRAIN_RATIO else 'val'
                
                # 이미지 및 라벨 저장
                cv2.imwrite(os.path.join(BASE_SAVE_DIR, split, 'images', filename), final_img)
                
                label_path = os.path.join(BASE_SAVE_DIR, split, 'labels', name + '.txt')
                with open(label_path, 'w', encoding='utf-8') as f:
                    f.write(f"{CLASS_ID} {' '.join(map(lambda x: f'{x:.6f}', yolo_bbox))}\n")
                
                valid_count += 1
                
            except Exception as e:
                # 에러 발생 시 로그를 보려면 주석 해제
                # print(f"Error processing {filename}: {e}")
                continue

    print(f"\n전처리 완료")
    print(f"저장 위치: {BASE_SAVE_DIR}")
    print(f"총 생성 데이터: {valid_count}쌍")

if __name__ == "__main__":
    process()
#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVE_NAME="${ARCHIVE_NAME:-backend.tar.gz}"
EC2_USER="${EC2_USER:-ubuntu}"
EC2_HOST="${EC2_HOST:-13.125.252.131}"
EC2_KEY_PATH="${EC2_KEY_PATH:-$HOME/Downloads/fitcommit-key.pem}"
REMOTE_DIR="${REMOTE_DIR:-~}"

cd "$PROJECT_ROOT"

if [ ! -d "Backend" ]; then
  echo "[deploy] Backend directory not found. Run this script from the project root."
  exit 1
fi

if [ ! -f "$EC2_KEY_PATH" ]; then
  echo "[deploy] EC2 key file not found: $EC2_KEY_PATH"
  echo "[deploy] Set EC2_KEY_PATH=/path/to/key.pem if your key lives elsewhere."
  exit 1
fi

echo "[deploy] Creating $ARCHIVE_NAME from Backend/"
tar \
  --exclude="Backend/.venv" \
  --exclude="Backend/venv" \
  --exclude="Backend/env" \
  --exclude="Backend/.env" \
  --exclude="Backend/.env.*" \
  --exclude="Backend/__pycache__" \
  --exclude="Backend/**/__pycache__" \
  --exclude="Backend/.pytest_cache" \
  --exclude="Backend/.mypy_cache" \
  --exclude="Backend/.ruff_cache" \
  --exclude=".git" \
  --exclude=".DS_Store" \
  --exclude="._*" \
  --exclude="$ARCHIVE_NAME" \
  -czf "$ARCHIVE_NAME" Backend

echo "[deploy] Uploading $ARCHIVE_NAME to $EC2_USER@$EC2_HOST:$REMOTE_DIR"
scp -i "$EC2_KEY_PATH" "$ARCHIVE_NAME" "$EC2_USER@$EC2_HOST:$REMOTE_DIR/"

echo "[deploy] Uploading restart helper to $EC2_USER@$EC2_HOST:$REMOTE_DIR/restart_backend.sh"
scp -i "$EC2_KEY_PATH" "Backend/deploy/restart_backend.sh" "$EC2_USER@$EC2_HOST:$REMOTE_DIR/restart_backend.sh"

echo "[deploy] Upload complete."
echo "[deploy] Next SSH into EC2 and run: bash ~/restart_backend.sh"

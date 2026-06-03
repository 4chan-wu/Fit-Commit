#!/usr/bin/env bash
set -euo pipefail

EC2_HOST="${EC2_HOST:-54.180.79.90}"
EC2_USER="${EC2_USER:-ubuntu}"
KEY_PATH="${KEY_PATH:-$HOME/Downloads/fitcommit-key.pem}"
REMOTE_DIR="${REMOTE_DIR:-/home/ubuntu}"
ARCHIVE_NAME="${ARCHIVE_NAME:-backend.tar.gz}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CURRENT_DIR="$(pwd)"
RESTART_AFTER_UPLOAD=false

if [ "${1:-}" = "--restart" ]; then
  RESTART_AFTER_UPLOAD=true
elif [ "${1:-}" != "" ]; then
  echo "[deploy] Unknown option: $1"
  echo "[deploy] Usage: ./scripts/deploy_backend_to_ec2.sh [--restart]"
  exit 1
fi

if [ "$CURRENT_DIR" != "$PROJECT_ROOT" ]; then
  echo "[deploy] Run this script from the project root:"
  echo "[deploy]   cd '$PROJECT_ROOT'"
  echo "[deploy]   ./scripts/deploy_backend_to_ec2.sh"
  exit 1
fi

if [ ! -d "Backend" ]; then
  echo "[deploy] Backend directory not found. Run this script from the project root."
  exit 1
fi

if [ ! -f "Backend/app/main.py" ]; then
  echo "[deploy] Backend/app/main.py not found. Check that you are deploying the Fitcommit project root."
  exit 1
fi

if [ ! -f "$KEY_PATH" ]; then
  echo "[deploy] EC2 key file not found: $KEY_PATH"
  echo "[deploy] Set KEY_PATH=/path/to/key.pem if your key lives elsewhere."
  exit 1
fi

if [ -f "$ARCHIVE_NAME" ]; then
  echo "[deploy] Removing existing $ARCHIVE_NAME"
  rm -f "$ARCHIVE_NAME"
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
  --exclude="Backend/.DS_Store" \
  --exclude="Backend/._*" \
  --exclude=".git" \
  --exclude=".DS_Store" \
  --exclude="._*" \
  --exclude="$ARCHIVE_NAME" \
  -czf "$ARCHIVE_NAME" Backend

echo "[deploy] Uploading $ARCHIVE_NAME to $EC2_USER@$EC2_HOST:$REMOTE_DIR"
scp -i "$KEY_PATH" "$ARCHIVE_NAME" "$EC2_USER@$EC2_HOST:$REMOTE_DIR/"

echo "[deploy] Uploading restart helper to $EC2_USER@$EC2_HOST:$REMOTE_DIR/restart_backend.sh"
scp -i "$KEY_PATH" "Backend/deploy/restart_backend.sh" "$EC2_USER@$EC2_HOST:$REMOTE_DIR/restart_backend.sh"

echo "[deploy] Upload complete."
echo "[deploy] Uploaded $ARCHIVE_NAME and restart_backend.sh successfully."

if [ "$RESTART_AFTER_UPLOAD" = true ]; then
  echo "[deploy] --restart enabled. Restarting Backend on EC2."
  ssh -i "$KEY_PATH" "$EC2_USER@$EC2_HOST" "bash '$REMOTE_DIR/restart_backend.sh'"
  echo "[deploy] Remote restart complete."
else
  echo "[deploy] Next SSH into EC2 and run: bash $REMOTE_DIR/restart_backend.sh"
fi

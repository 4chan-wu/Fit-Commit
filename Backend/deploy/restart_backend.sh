#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-fitcommit-backend}"
HOME_DIR="${HOME_DIR:-/home/ubuntu}"
ARCHIVE_PATH="${ARCHIVE_PATH:-$HOME_DIR/backend.tar.gz}"
BACKEND_DIR="${BACKEND_DIR:-$HOME_DIR/Backend}"
BACKUP_DIR="${BACKUP_DIR:-$HOME_DIR/Backend.backup.$(date +%Y%m%d%H%M%S)}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
SERVICE_NAME="${SERVICE_NAME:-fitcommit-backend}"
ENV_FILE="${ENV_FILE:-$HOME_DIR/fitcommit_backend.env}"

cd "$HOME_DIR"

if [ ! -f "$ARCHIVE_PATH" ]; then
  echo "[restart] Archive not found: $ARCHIVE_PATH"
  exit 1
fi

if [ -d "$BACKEND_DIR" ]; then
  echo "[restart] Backing up existing Backend to $BACKUP_DIR"
  mv "$BACKEND_DIR" "$BACKUP_DIR"
fi

echo "[restart] Extracting $ARCHIVE_PATH"
tar -xzf "$ARCHIVE_PATH" -C "$HOME_DIR"

if [ ! -f "$ENV_FILE" ]; then
  echo "[restart] WARNING: $ENV_FILE is missing."
  echo "[restart] Create it on EC2 with: sudo nano $ENV_FILE"
  echo "[restart] The systemd service reads this file for the Supabase DATABASE_URL and API keys."
fi

cd "$BACKEND_DIR"

if [ ! -d ".venv" ]; then
  echo "[restart] Creating Python virtual environment"
  "$PYTHON_BIN" -m venv .venv
fi

source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

echo "[restart] Stopping existing uvicorn processes if any"
pkill -f "uvicorn app.main:app" 2>/dev/null || true

if command -v systemctl >/dev/null 2>&1; then
  echo "[restart] Reloading systemd and restarting $SERVICE_NAME"
  sudo systemctl daemon-reload
  sudo systemctl restart "$SERVICE_NAME"
  sudo systemctl status "$SERVICE_NAME" --no-pager
  echo "[restart] Test with: curl 'http://54.180.79.90:8000/health'"
else
  echo "[restart] systemctl is not available. Start manually with:"
  echo "[restart] cd $BACKEND_DIR && source .venv/bin/activate && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"
fi

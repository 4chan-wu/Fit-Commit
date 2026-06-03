#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-fitcommit-backend}"
HOME_DIR="${HOME_DIR:-/home/ubuntu}"
ARCHIVE_PATH="${ARCHIVE_PATH:-$HOME_DIR/backend.tar.gz}"
BACKEND_DIR="${BACKEND_DIR:-$HOME_DIR/Backend}"
BACKUP_DIR="${BACKUP_DIR:-$HOME_DIR/Backend.backup.$(date +%Y%m%d%H%M%S)}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
PORT="${PORT:-8000}"
TMUX_SESSION="${TMUX_SESSION:-fitcommit}"

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

if [ -f "$BACKUP_DIR/.env" ] && [ ! -f "$BACKEND_DIR/.env" ]; then
  echo "[restart] Restoring .env from backup"
  cp "$BACKUP_DIR/.env" "$BACKEND_DIR/.env"
fi

if [ ! -f "$BACKEND_DIR/.env" ]; then
  echo "[restart] WARNING: $BACKEND_DIR/.env is missing."
  echo "[restart] Create it on EC2 with: nano $BACKEND_DIR/.env"
  echo "[restart] The server may fail until DATABASE_URL, SECRET_KEY, and Supabase env values are set."
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

if command -v tmux >/dev/null 2>&1; then
  echo "[restart] Restarting tmux session: $TMUX_SESSION"
  tmux kill-session -t "$TMUX_SESSION" 2>/dev/null || true
  tmux new-session -d -s "$TMUX_SESSION" "cd '$BACKEND_DIR' && source .venv/bin/activate && python -m uvicorn app.main:app --host 0.0.0.0 --port '$PORT'"
  echo "[restart] Backend started in tmux session '$TMUX_SESSION'"
  echo "[restart] Attach with: tmux attach -t $TMUX_SESSION"
  echo "[restart] Test with: curl 'http://13.125.252.131:8000/clothes?user_id=45482f0f-42d0-401e-a18a-77abeb0492cc'"
else
  echo "[restart] tmux is not installed. Starting uvicorn in the current shell."
  python -m uvicorn app.main:app --host 0.0.0.0 --port "$PORT"
fi

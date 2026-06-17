#!/bin/bash
# 启动服务（生产模式）
set -e

cd "$(dirname "$0")/.."

PORT=${PORT:-8000}
HOST=${HOST:-0.0.0.0}

echo "🚀 Starting Forge AI Python Service..."
echo "   Host: $HOST"
echo "   Port: $PORT"

uv run uvicorn src.main:app --host "$HOST" --port "$PORT"
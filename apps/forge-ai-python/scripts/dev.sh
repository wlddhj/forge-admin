#!/bin/bash
# 开发模式启动（带自动重载）
set -e

cd "$(dirname "$0")/.."

PORT=${PORT:-8000}

echo "🔧 Starting Forge AI Python Service (dev mode)..."
echo "   Port: $PORT"
echo "   Auto-reload: enabled"

uv run uvicorn src.main:app --reload --port "$PORT"
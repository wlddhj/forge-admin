#!/bin/bash
# 停止服务
set -e

PORT=${PORT:-8000}

echo "🛑 Stopping service on port $PORT..."

PID=$(lsof -i :$PORT -t 2>/dev/null || true)

if [ -n "$PID" ]; then
    kill $PID
    echo "✅ Service stopped (PID: $PID)"
else
    echo "⚠️  No service running on port $PORT"
fi
#!/bin/bash
# 完整维护命令
set -e

cd "$(dirname "$0")/.."

CMD=${1:-help}

case "$CMD" in
    install)
        echo "📦 Installing dependencies..."
        uv sync --all-groups
        ;;
    start)
        echo "🚀 Starting service..."
        ./scripts/start.sh
        ;;
    dev)
        echo "🔧 Starting dev server..."
        ./scripts/dev.sh
        ;;
    stop)
        echo "🛑 Stopping service..."
        ./scripts/stop.sh
        ;;
    test)
        echo "🧪 Running tests..."
        uv run pytest tests/ -v
        ;;
    lint)
        echo "🔍 Running lint..."
        uv run ruff check src/
        uv run ruff format --check src/
        ;;
    format)
        echo "🎨 Formatting code..."
        uv run ruff format src/
        ;;
    clean)
        echo "🧹 Cleaning cache..."
        find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
        find . -type d -name ".pytest_cache" -exec rm -rf {} + 2>/dev/null || true
        find . -type f -name "*.pyc" -delete
        ;;
    status)
        PORT=${PORT:-8000}
        PID=$(lsof -i :$PORT -t 2>/dev/null || true)
        if [ -n "$PID" ]; then
            echo "✅ Service running on port $PORT (PID: $PID)"
        else
            echo "⚠️  No service running on port $PORT"
        fi
        ;;
    help|*)
        echo "Forge AI Python Service - Maintenance Commands"
        echo ""
        echo "Usage: ./scripts/run.sh <command>"
        echo ""
        echo "Commands:"
        echo "  install  - Install dependencies"
        echo "  start    - Start production server"
        echo "  dev      - Start dev server (with reload)"
        echo "  stop     - Stop running server"
        echo "  test     - Run tests"
        echo "  lint     - Check code style"
        echo "  format   - Format code"
        echo "  clean    - Clean cache files"
        echo "  status   - Check service status"
        echo "  help     - Show this help"
        ;;
esac
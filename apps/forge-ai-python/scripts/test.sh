#!/bin/bash
# 运行测试
set -e

cd "$(dirname "$0")/.."

echo "🧪 Running tests..."

uv run pytest tests/ -v

echo "✅ Tests complete!"
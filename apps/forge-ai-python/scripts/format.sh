#!/bin/bash
# 代码格式化
set -e

cd "$(dirname "$0")/.."

echo "🎨 Formatting code..."
uv run ruff format src/

echo "✅ Format complete!"
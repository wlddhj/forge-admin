#!/bin/bash
# 代码检查（lint + format）
set -e

cd "$(dirname "$0")/.."

echo "🔍 Running ruff check..."
uv run ruff check src/

echo "🎨 Running ruff format check..."
uv run ruff format --check src/

echo "✅ Lint complete!"
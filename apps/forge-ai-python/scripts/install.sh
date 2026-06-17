#!/bin/bash
# 安装依赖
set -e

cd "$(dirname "$0")/.."

echo "🔍 Checking uv installation..."
if ! command -v uv &> /dev/null; then
    echo "❌ uv not found. Installing uv..."
    curl -LsSf https://astral.sh/uv/install.sh | sh
fi

echo "✅ uv version: $(uv --version)"

echo "📦 Installing dependencies..."
uv sync

echo "📦 Installing dev dependencies..."
uv sync --all-groups

echo "✅ Installation complete!"
echo ""
echo "Run './scripts/start.sh' to start the service."
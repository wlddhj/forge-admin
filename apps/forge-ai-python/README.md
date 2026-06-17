# Forge AI Python Service

AI service for Forge Admin - multi-provider LLM integration with document parsing capabilities.

## Features

- **Multi-Provider LLM Support**: DeepSeek, Qwen (Tongyi), GLM (Zhipu), ERNIE (Baidu)
- **Document Parsing**: PDF, DOCX, TXT file extraction
- **Document Summarization**: AI-powered text summarization with multiple styles
- **Streaming Chat**: Real-time streaming responses via SSE

## Quick Start

### Prerequisites

- Python 3.10+
- uv (recommended) or pip

### Using Maintenance Scripts (uv)

```bash
# 安装依赖
./scripts/install.sh

# 开发模式启动（带自动重载）
./scripts/dev.sh

# 生产模式启动
./scripts/start.sh

# 停止服务
./scripts/stop.sh

# 运行测试
./scripts/test.sh

# 代码检查
./scripts/lint.sh

# 代码格式化
./scripts/format.sh

# 查看所有命令
./scripts/run.sh help
```

### Manual Installation (pip)

```bash
# Clone and enter directory
cd apps/forge-ai-python

# Install dependencies
pip install -e .
pip install -e ".[dev]"  # For development
```

### Manual Installation (uv)

```bash
cd apps/forge-ai-python

# Install uv if not already installed
curl -LsSf https://astral.sh/uv/install.sh | sh

# Install dependencies
uv sync

# Install with dev dependencies
uv sync --all-groups
```

### Configuration

Create `.env` file from example:

```bash
cp .env.example .env
```

Configure your LLM provider API keys:

```env
# DeepSeek
DEEPSEEK_API_KEY=your_deepseek_api_key

# Alibaba Qwen
QWEN_API_KEY=your_qwen_api_key

# Zhipu GLM
GLM_API_KEY=your_glm_api_key

# Baidu ERNIE
ERNIE_API_KEY=your_ernie_api_key
ERNIE_SECRET_KEY=your_ernie_secret_key

# Default provider
DEFAULT_PROVIDER=deepseek
```

### Running

```bash
# Development mode
python -m uvicorn src.main:app --reload --port 8000

# Production mode
python -m uvicorn src.main:app --host 0.0.0.0 --port 8000
```

### Docker

```bash
# Build image
docker build -t forge-ai-python .

# Run container
docker run -p 8000:8000 --env-file .env forge-ai-python
```

## API Endpoints

### Health Check

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health` | GET | Service health status |
| `/api/health/ready` | GET | Readiness check |
| `/api/health/live` | GET | Liveness check |

### Chat

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/chat/completions` | POST | Chat completion |
| `/api/chat/completions/stream` | POST | Streaming chat (SSE) |
| `/api/chat/providers` | GET | List available providers |

### Document

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/document/types` | GET | Supported document types |
| `/api/document/parse` | POST | Parse uploaded document |
| `/api/document/summarize` | POST | Summarize text content |

## API Examples

### Chat Completion

```bash
curl -X POST http://localhost:8000/api/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "Hello!"}],
    "provider": "deepseek"
  }'
```

### Document Summarization

```bash
curl -X POST http://localhost:8000/api/document/summarize \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Long document content...",
    "style": "brief",
    "max_length": 500
  }'
```

## Testing

```bash
# Run tests
pytest

# With coverage
pytest --cov=src

# Run specific test
pytest tests/test_health.py
```

## Development

### Code Style

```bash
# Lint
ruff check src/

# Format
ruff format src/

# Type check
mypy src/
```

### Project Structure

```
src/
├── api/            # FastAPI endpoints
│   ├── chat.py     # Chat completion API
│   ├── document.py # Document parsing API
│   ├── health.py   # Health check API
│   └── router.py   # Router configuration
├── adapters/       # LLM provider adapters
│   ├── base.py     # Abstract base adapter
│   ├── factory.py  # Adapter factory
│   ├── deepseek.py # DeepSeek adapter
│   ├── qwen.py     # Qwen adapter
│   ├── glm.py      # GLM adapter
│   └── ernie.py    # ERNIE adapter
├── config/         # Configuration
│   ├── settings.py # Environment settings
│   └── model_config.py # Model configurations
├── models/         # Pydantic schemas
│   └── schemas.py  # API request/response models
├── services/       # Business logic
│   ├── document_parser.py # Document parsing
│   ├── llm_client.py      # LLM client
│   └── summarizer.py      # Summarization
├── utils/          # Utilities
│   └── http_client.py # HTTP client
└── main.py         # Application entry point
```

## License

MIT
"""Pydantic schemas for API request/response models."""

from datetime import datetime
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field


# ============== Common ==============
class HealthResponse(BaseModel):
    """Health check response."""

    status: Literal["healthy", "unhealthy"] = "healthy"
    version: str
    timestamp: datetime = Field(default_factory=datetime.utcnow)
    providers: list[str] = []


# ============== Chat ==============
class ChatMessage(BaseModel):
    """Chat message."""

    role: Literal["system", "user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    """Chat completion request."""

    messages: list[ChatMessage]
    provider: Optional[Literal["deepseek", "qwen", "glm", "ernie"]] = None
    model: Optional[str] = None
    temperature: Optional[float] = Field(default=None, ge=0, le=2)
    max_tokens: Optional[int] = Field(default=None, ge=1, le=32768)
    stream: bool = False


class ChatChoice(BaseModel):
    """Chat completion choice."""

    index: int
    message: ChatMessage
    finish_reason: Optional[str] = None


class ChatUsage(BaseModel):
    """Token usage information."""

    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


class ChatResponse(BaseModel):
    """Chat completion response."""

    id: str
    provider: str
    model: str
    choices: list[ChatChoice]
    usage: Optional[ChatUsage] = None
    created: datetime = Field(default_factory=datetime.utcnow)


# ============== Document ==============
class DocumentType(BaseModel):
    """Supported document type."""

    extension: str
    mime_type: str
    description: str


class DocumentParseRequest(BaseModel):
    """Document parse request."""

    filename: str
    content_type: str
    size: int


class DocumentParseResponse(BaseModel):
    """Document parse response."""

    content: Optional[str] = None  # 文档内容，对应 Java 的 content 字段
    pages: int = 1
    metadata: dict[str, Any] = Field(default_factory=dict)
    # 以下字段用于匹配 Java 端的 DocumentResponse
    status: int = 1  # 1-成功 2-失败
    summary: Optional[str] = None
    modelName: Optional[str] = None
    errorMessage: Optional[str] = None


class SummarizeRequest(BaseModel):
    """Document summarize request."""

    text: str
    provider: Optional[Literal["deepseek", "qwen", "glm", "ernie"]] = None
    max_length: int = Field(default=500, ge=100, le=2000)
    style: Literal["brief", "detailed", "bullet"] = "brief"


class SummarizeResponse(BaseModel):
    """Document summarize response."""

    summary: str
    provider: str
    model: str
    original_length: int
    summary_length: int


# ============== Provider Info ==============
class ProviderInfo(BaseModel):
    """Provider information."""

    name: str
    model: str
    available: bool
    api_base: str


class ProvidersResponse(BaseModel):
    """List of available providers."""

    providers: list[ProviderInfo]
    default: str
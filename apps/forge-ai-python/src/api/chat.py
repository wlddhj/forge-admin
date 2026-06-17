"""Chat API endpoints."""

import json
from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from models.schemas import ChatRequest, ChatResponse, ProvidersResponse
from services.llm_client import LLMClient

router = APIRouter()


@router.post("/completions", response_model=ChatResponse)
async def chat_completions(request: ChatRequest) -> ChatResponse:
    """Send chat completion request."""
    llm_client = LLMClient(request.provider)

    # Check if provider is available
    available_providers = LLMClient.get_available_providers()
    provider = request.provider or "deepseek"  # Default provider

    if provider not in available_providers:
        raise HTTPException(
            status_code=400,
            detail=f"Provider '{provider}' not available. Available: {available_providers}",
        )

    try:
        return await llm_client.chat(
            messages=request.messages,
            provider=request.provider,
            temperature=request.temperature or 0.7,
            max_tokens=request.max_tokens or 4096,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/completions/stream")
async def chat_completions_stream(request: ChatRequest) -> StreamingResponse:
    """Send streaming chat completion request."""
    llm_client = LLMClient(request.provider)

    # Check if provider is available
    available_providers = LLMClient.get_available_providers()
    provider = request.provider or "deepseek"

    if provider not in available_providers:
        raise HTTPException(
            status_code=400,
            detail=f"Provider '{provider}' not available. Available: {available_providers}",
        )

    async def generate():
        try:
            async for chunk in llm_client.chat_stream(
                messages=request.messages,
                provider=request.provider,
                temperature=request.temperature or 0.7,
                max_tokens=request.max_tokens or 4096,
            ):
                # 使用JSON格式返回，避免换行符破坏SSE格式
                yield f"data: {json.dumps({'content': chunk})}\n\n"
            yield "data: [DONE]\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)})}\n\n"

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        },
    )


@router.get("/providers", response_model=ProvidersResponse)
async def get_providers() -> ProvidersResponse:
    """Get available LLM providers."""
    from adapters.factory import AdapterFactory
    from config.model_config import get_model_config
    from config.settings import get_settings

    settings = get_settings()
    available = AdapterFactory.get_available_providers()

    providers = []
    for name in ["deepseek", "qwen", "glm", "ernie"]:
        config = get_model_config(name)
        providers.append({
            "name": name,
            "model": config.model_name,
            "available": name in available,
            "api_base": config.api_base,
        })

    return ProvidersResponse(
        providers=providers,
        default=settings.default_provider,
    )
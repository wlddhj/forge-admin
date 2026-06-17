"""Qwen (Tongyi Qianwen) LLM adapter."""

import json
import uuid
from typing import Any, AsyncIterator

import httpx

from adapters.base import BaseLLMAdapter
from models.schemas import ChatMessage


class QwenAdapter(BaseLLMAdapter):
    """Adapter for Alibaba Qwen API."""

    @property
    def provider_name(self) -> str:
        return "qwen"

    async def chat(
        self,
        messages: list[ChatMessage],
        temperature: float = 0.7,
        max_tokens: int = 4096,
        **kwargs: Any,
    ) -> "ChatResponse":
        """Send chat completion request to Qwen."""
        url = f"{self.api_base}/services/aigc/text-generation/generation"
        headers = self._build_headers()
        payload = self._build_payload(messages, temperature, max_tokens, **kwargs)

        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()

        return self._parse_response(data)

    async def chat_stream(
        self,
        messages: list[ChatMessage],
        temperature: float = 0.7,
        max_tokens: int = 4096,
        **kwargs: Any,
    ) -> AsyncIterator[str]:
        """Send streaming chat completion request to Qwen.

        With incremental_output=True, each SSE event contains only new content.
        SSE format:
        id:1
        event:result
        :HTTP_STATUS/200
        data:{...output.choices[0].message.content...}
        """
        url = f"{self.api_base}/services/aigc/text-generation/generation"
        headers = self._build_headers()
        payload = self._build_payload(messages, temperature, max_tokens, stream=True, **kwargs)

        async with httpx.AsyncClient(timeout=60.0) as client:
            async with client.stream("POST", url, headers=headers, json=payload) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    line = line.strip()
                    if not line:
                        continue
                    if line.startswith("data:"):
                        data_str = line[5:].strip()
                        if data_str:
                            try:
                                data = json.loads(data_str)
                                output = data.get("output", {})
                                choices = output.get("choices", [])
                                if choices:
                                    message = choices[0].get("message", {})
                                    content = message.get("content", "")
                                    # With incremental_output=True, content is already incremental
                                    if content:
                                        yield content
                                    # Check finish reason
                                    finish_reason = choices[0].get("finish_reason", "")
                                    if finish_reason and finish_reason != "null":
                                        break
                            except json.JSONDecodeError:
                                continue

    async def count_tokens(self, text: str) -> int:
        """Estimate token count for Qwen models."""
        return len(text) // 4

    def _build_headers(self) -> dict[str, str]:
        """Build request headers."""
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }

    def _build_payload(
        self,
        messages: list[ChatMessage],
        temperature: float,
        max_tokens: int,
        stream: bool = False,
        **kwargs: Any,
    ) -> dict[str, Any]:
        """Build request payload for Qwen API format."""
        formatted_messages = []
        for m in messages:
            formatted_messages.append({"role": m.role, "content": m.content})

        payload = {
            "model": self.model_name,
            "input": {"messages": formatted_messages},
            "parameters": {
                "temperature": temperature,
                "max_tokens": max_tokens,
                "result_format": "message",
                "incremental_output": True,
            },
            "stream": stream,
        }
        payload.update(kwargs)
        return payload

    def _parse_response(self, data: dict[str, Any]) -> "ChatResponse":
        """Parse API response into ChatResponse."""
        output = data.get("output", {})
        usage = data.get("usage", {})

        content = output.get("text", "")
        if not content:
            choices = output.get("choices", [{}])
            if choices:
                content = choices[0].get("message", {}).get("content", "")

        return self._build_chat_response(
            content=content,
            prompt_tokens=usage.get("input_tokens", 0),
            completion_tokens=usage.get("output_tokens", 0),
            response_id=data.get("request_id", str(uuid.uuid4())),
        )
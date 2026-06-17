"""Document API endpoints."""

from fastapi import APIRouter, File, HTTPException, UploadFile, Form, Request
from typing import Optional
import json

from models.schemas import DocumentParseResponse, DocumentType, SummarizeRequest, SummarizeResponse
from services.document_parser import DocumentParser
from services.summarizer import Summarizer

router = APIRouter()
document_parser = DocumentParser()


@router.get("/types", response_model=list[DocumentType])
async def get_document_types() -> list[DocumentType]:
    """Get supported document types."""
    types = document_parser.get_supported_types()
    return [DocumentType(**t) for t in types]


@router.post("/parse", response_model=DocumentParseResponse)
async def parse_document(
    request: Request,
    file: Optional[UploadFile] = File(default=None),
    filePath: Optional[str] = Form(default=None),
    documentId: Optional[int] = Form(default=None)
) -> DocumentParseResponse:
    """Parse uploaded document and extract text.

    支持三种方式：
    1. multipart/form-data上传文件：file参数
    2. multipart/form-data指定路径：filePath参数
    3. JSON body：{"filePath": "...", "documentId": ...}
    """
    content = None
    filename = "unknown"

    # 尝试从JSON body获取参数
    content_type = request.headers.get("content-type", "")
    if "application/json" in content_type:
        try:
            body = await request.json()
            filePath = body.get("filePath")
            documentId = body.get("documentId")
        except json.JSONDecodeError:
            pass

    if file and file.filename:
        # 方式1：上传文件
        content = await file.read()
        filename = file.filename
    elif filePath:
        # 方式2/3：从本地路径读取文件
        import os
        if not os.path.exists(filePath):
            raise HTTPException(status_code=404, detail=f"File not found: {filePath}")
        with open(filePath, 'rb') as f:
            content = f.read()
        filename = os.path.basename(filePath)
    else:
        raise HTTPException(status_code=422, detail="Either 'file' or 'filePath' must be provided")

    try:
        result = await document_parser.parse(content, filename)
        return DocumentParseResponse(
            content=result["text"],
            pages=result["pages"],
            metadata=result["metadata"],
            status=1,  # 成功
            summary=None,
            modelName=None,
            errorMessage=None,
        )
    except ValueError as e:
        return DocumentParseResponse(
            content=None,
            pages=0,
            metadata={},
            status=2,  # 失败
            summary=None,
            modelName=None,
            errorMessage=str(e),
        )
    except Exception as e:
        return DocumentParseResponse(
            content=None,
            pages=0,
            metadata={},
            status=2,  # 失败
            summary=None,
            modelName=None,
            errorMessage=f"Failed to parse document: {str(e)}",
        )


@router.post("/summarize", response_model=SummarizeResponse)
async def summarize_document(request: SummarizeRequest) -> SummarizeResponse:
    """Summarize text content."""
    summarizer = Summarizer(request.provider)

    # Check if provider is available
    from services.llm_client import LLMClient

    available_providers = LLMClient.get_available_providers()
    provider = request.provider or "deepseek"

    if provider not in available_providers:
        raise HTTPException(
            status_code=400,
            detail=f"Provider '{provider}' not available. Available: {available_providers}",
        )

    try:
        return await summarizer.summarize(
            text=request.text,
            style=request.style,
            max_length=request.max_length,
            provider=request.provider,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
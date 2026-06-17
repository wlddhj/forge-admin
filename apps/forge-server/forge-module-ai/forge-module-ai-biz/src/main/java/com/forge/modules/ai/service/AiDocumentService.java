package com.forge.modules.ai.service;

import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.DocumentResponse;

import java.util.List;

/**
 * AI文档服务接口
 */
public interface AiDocumentService {

    /**
     * 解析文档
     */
    DocumentResponse parseDocument(Long documentId, String filePath);

    /**
     * 生成文档摘要
     */
    DocumentResponse summarize(DocumentSummaryRequest request);

    /**
     * 获取文档列表
     */
    List<DocumentResponse> getDocumentList();

    /**
     * 获取文档详情
     */
    DocumentResponse getDocument(Long documentId);

    /**
     * 删除文档
     */
    void deleteDocument(Long documentId);
}
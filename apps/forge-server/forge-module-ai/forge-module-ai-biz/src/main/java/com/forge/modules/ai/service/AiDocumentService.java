package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.forge.modules.ai.dto.request.DocumentQueryRequest;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI文档服务接口
 */
public interface AiDocumentService {

    /**
     * 分页查询文档列表
     */
    IPage<DocumentResponse> pageDocument(DocumentQueryRequest request);

    /**
     * 解析文档（通过文件路径）
     */
    DocumentResponse parseDocument(Long documentId, String filePath);

    /**
     * 解析文档（通过上传文件）
     */
    DocumentResponse parseDocumentFile(Long documentId, MultipartFile file);

    /**
     * 生成文档摘要
     */
    DocumentResponse summarize(DocumentSummaryRequest request);

    /**
     * 生成文档摘要（使用默认模型）
     */
    DocumentResponse generateSummary(Long documentId);

    /**
     * 获取文档详情
     */
    DocumentResponse getDocument(Long documentId);

    /**
     * 删除文档
     */
    void deleteDocument(Long documentId);
}
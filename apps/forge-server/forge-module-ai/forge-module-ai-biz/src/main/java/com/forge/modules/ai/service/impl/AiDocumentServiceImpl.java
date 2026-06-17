package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.client.PythonAiClient;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.entity.AiDocument;
import com.forge.modules.ai.mapper.AiDocumentMapper;
import com.forge.modules.ai.service.AiDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI文档服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentServiceImpl implements AiDocumentService {

    private final PythonAiClient pythonAiClient;
    private final AiDocumentMapper documentMapper;

    @Override
    @Transactional
    public DocumentResponse parseDocument(Long documentId, String filePath) {
        AiDocument document = null;
        if (documentId != null) {
            document = documentMapper.selectById(documentId);
            if (document != null) {
                document.setStatus(0);
                documentMapper.updateById(document);
            }
        }

        // 调用Python服务解析
        DocumentResponse response = pythonAiClient.parseDocument(documentId, filePath);

        // 更新文档状态和内容
        if (document != null && response != null) {
            document.setStatus(response.getStatus() == 1 ? 1 : 2);
            document.setSummary(response.getSummary());
            document.setModelName(response.getModelName());
            documentMapper.updateById(document);
        }

        return response;
    }

    @Override
    @Transactional
    public DocumentResponse summarize(DocumentSummaryRequest request) {
        DocumentResponse response = pythonAiClient.summarize(request);

        // 更新文档摘要
        if (response != null && response.getStatus() != null && response.getStatus() == 1) {
            AiDocument document = documentMapper.selectById(request.getDocumentId());
            if (document != null) {
                document.setSummary(response.getSummary());
                document.setModelName(response.getModelName());
                documentMapper.updateById(document);
            }
        }

        return response;
    }

    @Override
    public List<DocumentResponse> getDocumentList() {
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiDocument::getCreateTime);
        List<AiDocument> documents = documentMapper.selectList(wrapper);
        return documents.stream().map(this::toDocumentResponse).collect(Collectors.toList());
    }

    @Override
    public DocumentResponse getDocument(Long documentId) {
        AiDocument document = documentMapper.selectById(documentId);
        return document != null ? toDocumentResponse(document) : null;
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        documentMapper.deleteById(documentId);
    }

    private DocumentResponse toDocumentResponse(AiDocument document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setFileName(document.getFileName());
        response.setFileType(document.getFileType());
        response.setFileSize(document.getFileSize());
        response.setFileUrl(document.getFileUrl());
        response.setSummary(document.getSummary());
        response.setModelName(document.getModelName());
        response.setStatus(document.getStatus());
        response.setCreateTime(document.getCreateTime());
        response.setUpdateTime(document.getUpdateTime());
        return response;
    }
}
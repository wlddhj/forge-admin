package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.DocumentQueryRequest;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.entity.AiDocument;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiDocumentMapper;
import com.forge.modules.ai.service.AiDocumentService;
import com.forge.modules.ai.service.AiModelResolver;
import com.forge.modules.ai.service.AiModelService;
import com.forge.modules.ai.service.DocumentParser;
import com.forge.modules.ai.service.DocumentSummarizer;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.SysAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI文档服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentServiceImpl implements AiDocumentService {

    private final DocumentParser documentParser;
    private final DocumentSummarizer documentSummarizer;
    private final AiModelResolver modelResolver;
    private final AiDocumentMapper documentMapper;
    private final SysAttachmentService attachmentService;
    private final AiModelService modelService;

    @Override
    public IPage<DocumentResponse> pageDocument(DocumentQueryRequest request) {
        Page<AiDocument> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<>();

        if (request.getFileName() != null && !request.getFileName().isEmpty()) {
            wrapper.like(AiDocument::getFileName, request.getFileName());
        }

        if (request.getStatus() != null) {
            wrapper.eq(AiDocument::getStatus, request.getStatus());
        }

        Long queryUserId = request.getUserId();
        if (queryUserId == null && !SecurityHelper.isAdmin()) {
            queryUserId = SecurityHelper.getCurrentUserId();
        }
        if (queryUserId != null) {
            wrapper.eq(AiDocument::getUserId, queryUserId);
        }

        wrapper.orderByDesc(AiDocument::getCreateTime);

        Page<AiDocument> result = documentMapper.selectPage(page, wrapper);
        return result.convert(doc -> {
            SysAttachment attachment = doc.getAttachmentId() != null
                ? attachmentService.getById(doc.getAttachmentId()) : null;
            return toDocumentResponse(doc, attachment);
        });
    }

    @Override
    @Transactional
    public DocumentResponse parseDocument(Long documentId, String filePath) {
        AiDocument document = documentId != null ? documentMapper.selectById(documentId) : null;
        if (document == null) {
            DocumentResponse response = new DocumentResponse();
            response.setStatus(2);
            response.setErrorMessage("文档不存在");
            return response;
        }

        document.setStatus(0);
        documentMapper.updateById(document);

        // 优先取附件本地文件；入参 filePath 作为兜底（兼容历史调用）
        byte[] bytes = readAttachmentBytes(document.getAttachmentId(), filePath);
        if (bytes == null) {
            markParseFailure(document, "无法读取文档文件，仅支持本地存储附件的解析");
        } else {
            applyParseResult(document, bytes);
        }

        SysAttachment attachment = document.getAttachmentId() != null
            ? attachmentService.getById(document.getAttachmentId()) : null;
        return toDocumentResponse(document, attachment);
    }

    @Override
    @Transactional
    public DocumentResponse parseDocumentFile(Long documentId, MultipartFile file) {
        AiDocument document = null;

        if (documentId == null) {
            AttachmentResponse attachment = attachmentService.upload(file, "ai_document", null);

            document = new AiDocument();
            document.setUserId(SecurityHelper.getCurrentUserId());
            document.setAttachmentId(attachment.getId());
            document.setFileName(attachment.getOriginalName());
            document.setStatus(0);
            documentMapper.insert(document);
            documentId = document.getId();

            try {
                applyParseResult(document, file.getBytes());
            } catch (Exception e) {
                markParseFailure(document, e.getMessage());
            }
        } else {
            document = documentMapper.selectById(documentId);
            if (document != null) {
                document.setStatus(0);
                documentMapper.updateById(document);
            }
        }

        SysAttachment attachment = document != null && document.getAttachmentId() != null
            ? attachmentService.getById(document.getAttachmentId()) : null;
        return document != null ? toDocumentResponse(document, attachment) : null;
    }

    @Override
    @Transactional
    public DocumentResponse generateSummary(Long documentId) {
        AiDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }
        if (document.getContent() == null || document.getContent().isEmpty()) {
            throw new RuntimeException("文档内容为空，无法生成摘要");
        }

        AiModelConfig defaultModel = modelService.getDefaultModel();
        if (defaultModel == null) {
            throw new RuntimeException("请先配置默认AI模型");
        }

        DocumentResponse response = documentSummarizer.summarize(document.getContent(), "brief", 500, defaultModel);

        if (response.getStatus() != null && response.getStatus() == 1) {
            document.setSummary(response.getSummary());
            document.setModelName(defaultModel.getModelCode());
            documentMapper.updateById(document);
        }

        SysAttachment attachment = document.getAttachmentId() != null
            ? attachmentService.getById(document.getAttachmentId()) : null;
        return toDocumentResponse(document, attachment);
    }

    @Override
    @Transactional
    public DocumentResponse summarize(DocumentSummaryRequest request) {
        ChatRequest resolveRequest = new ChatRequest();
        resolveRequest.setModelName(request.getModelName());
        AiModelConfig modelConfig = modelResolver.resolve(resolveRequest);
        if (modelConfig == null) {
            DocumentResponse response = new DocumentResponse();
            response.setStatus(2);
            response.setErrorMessage("没有可用的模型配置，请先在模型管理中启用模型");
            return response;
        }

        DocumentResponse response = documentSummarizer.summarize(
                request.getText(), request.getStyle(), request.getMaxLength(), modelConfig);

        if (response.getStatus() != null && response.getStatus() == 1) {
            AiDocument document = documentMapper.selectById(request.getDocumentId());
            if (document != null) {
                document.setSummary(response.getSummary());
                document.setModelName(modelConfig.getModelName());
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
        return documents.stream()
            .map(doc -> {
                SysAttachment attachment = doc.getAttachmentId() != null
                    ? attachmentService.getById(doc.getAttachmentId()) : null;
                return toDocumentResponse(doc, attachment);
            })
            .collect(Collectors.toList());
    }

    @Override
    public DocumentResponse getDocument(Long documentId) {
        AiDocument document = documentMapper.selectById(documentId);
        if (document == null) return null;

        SysAttachment attachment = null;
        if (document.getAttachmentId() != null) {
            attachment = attachmentService.getById(document.getAttachmentId());
        }
        return toDocumentResponse(document, attachment);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        AiDocument document = documentMapper.selectById(documentId);
        if (document != null) {
            if (document.getAttachmentId() != null) {
                attachmentService.deleteAttachments(List.of(document.getAttachmentId()));
            }
            documentMapper.deleteById(documentId);
        }
    }

    private void applyParseResult(AiDocument document, byte[] bytes) {
        try {
            String content = documentParser.parse(bytes, document.getFileName());
            document.setStatus(1);
            document.setContent(content);
            document.setErrorMessage(null);
        } catch (IllegalArgumentException e) {
            markParseFailure(document, e.getMessage());
        }
        documentMapper.updateById(document);
    }

    private void markParseFailure(AiDocument document, String errorMessage) {
        document.setStatus(2);
        document.setErrorMessage(errorMessage);
        documentMapper.updateById(document);
    }

    private byte[] readAttachmentBytes(Long attachmentId, String fallbackPath) {
        try {
            if (attachmentId != null) {
                SysAttachment attachment = attachmentService.getById(attachmentId);
                if (attachment != null && attachment.getFilePath() != null) {
                    return readLocalFile(attachment.getFilePath());
                }
            }
            if (fallbackPath != null) {
                return readLocalFile(fallbackPath);
            }
        } catch (Exception e) {
            log.warn("读取附件文件失败: {}", e.getMessage());
        }
        return null;
    }

    private byte[] readLocalFile(String filePath) throws Exception {
        Path path = Path.of(filePath);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir"), filePath);
        }
        return Files.readAllBytes(path);
    }

    private DocumentResponse toDocumentResponse(AiDocument document, SysAttachment attachment) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setFileName(document.getFileName());
        if (attachment != null) {
            response.setFileType(attachment.getFileExtension());
            response.setFileSize(attachment.getFileSize());
            response.setFileUrl(attachment.getFileUrl());
        }
        response.setContent(document.getContent());
        response.setSummary(document.getSummary());
        response.setModelName(document.getModelName());
        response.setStatus(document.getStatus());
        response.setErrorMessage(document.getErrorMessage());
        response.setCreateTime(document.getCreateTime());
        response.setUpdateTime(document.getUpdateTime());
        return response;
    }
}
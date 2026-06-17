package com.forge.modules.ai.controller;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.service.AiDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI文档控制器
 */
@Slf4j
@Tag(name = "AI文档管理")
@RestController
@RequestMapping("/ai/document")
@RequiredArgsConstructor
public class AiDocumentController {

    private final AiDocumentService aiDocumentService;

    @Operation(summary = "上传文档")
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('ai:document:upload')")
    @OperationLog(title = "AI文档管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<DocumentResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        // 上传并解析文档
        String filePath = file.getOriginalFilename();
        DocumentResponse response = aiDocumentService.parseDocument(null, filePath);
        return Result.success(response);
    }

    @Operation(summary = "获取文档列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ai:document:query')")
    public Result<List<DocumentResponse>> getDocumentList() {
        List<DocumentResponse> list = aiDocumentService.getDocumentList();
        return Result.success(list);
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:document:query')")
    public Result<DocumentResponse> getDocument(@PathVariable Long id) {
        DocumentResponse response = aiDocumentService.getDocument(id);
        return Result.success(response);
    }

    @Operation(summary = "生成文档摘要")
    @PostMapping("/summary")
    @PreAuthorize("hasAuthority('ai:document:summary')")
    @OperationLog(title = "AI文档管理", businessType = OperationLog.BusinessType.OTHER)
    public Result<DocumentResponse> summarizeDocument(@Valid @RequestBody DocumentSummaryRequest request) {
        DocumentResponse response = aiDocumentService.summarize(request);
        return Result.success(response);
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:document:delete')")
    @OperationLog(title = "AI文档管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> deleteDocument(@PathVariable Long id) {
        aiDocumentService.deleteDocument(id);
        return Result.success();
    }
}
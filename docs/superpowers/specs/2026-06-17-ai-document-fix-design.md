# AI文档管理功能修复设计

## 背景

当前文档管理存在两个问题：
1. 文件上传未使用系统附件服务，文件存储缺乏统一管理
2. 摘要生成功能前后端 API 不匹配，无法正常调用

## 设计决策

- **文件上传**：集成系统附件服务，通过 attachment_id 关联附件表
- **摘要模型**：使用系统默认模型配置，无需用户手动选择
- **数据冗余**：移除 ai_document 表中的冗余文件字段，通过附件表获取

## 详细设计

### 1. 数据库修改

```sql
-- 新增附件关联字段
ALTER TABLE `ai_document` ADD COLUMN `attachment_id` bigint DEFAULT NULL COMMENT '附件ID' AFTER `user_id`;
ALTER TABLE `ai_document` ADD KEY `idx_attachment_id` (`attachment_id`);

-- 移除冗余字段（文件信息通过附件表获取）
ALTER TABLE `ai_document` DROP COLUMN `file_path`;
ALTER TABLE `ai_document` DROP COLUMN `file_url`;
ALTER TABLE `ai_document` DROP COLUMN `file_size`;
ALTER TABLE `ai_document` DROP COLUMN `file_type`;
```

### 2. 后端修改

#### 2.1 AiDocument 实体 (forge-module-ai-api)

```java
@Data
@TableName("ai_document")
public class AiDocument {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long attachmentId;  // 新增：关联附件ID
    private String fileName;    // 保留：文件名用于显示
    private String content;
    private String summary;
    private String modelName;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
```

#### 2.2 AiDocumentService (forge-module-ai-biz)

**依赖注入**：
```java
private final SysAttachmentService attachmentService;
```

**上传流程**：
```java
@Transactional
public DocumentResponse uploadDocument(MultipartFile file) {
    // 1. 上传到附件服务
    AttachmentResponse attachment = attachmentService.upload(file, "ai_document", null);

    // 2. 创建文档记录
    AiDocument document = new AiDocument();
    document.setUserId(SecurityHelper.getCurrentUserId());
    document.setAttachmentId(attachment.getId());
    document.setFileName(attachment.getOriginalName());
    document.setStatus(0);  // 处理中
    documentMapper.insert(document);

    // 3. 调用 Python 解析文档内容
    DocumentResponse response = pythonAiClient.parseDocument(document.getId(), attachment.getFilePath());

    // 4. 更新文档状态
    document.setStatus(response.getStatus() == 1 ? 1 : 2);
    document.setContent(response.getText());
    if (response.getErrorMessage() != null) {
        document.setErrorMessage(response.getErrorMessage());
    }
    documentMapper.updateById(document);

    return toDocumentResponse(document, attachment);
}
```

**删除流程**：
```java
@Transactional
public void deleteDocument(Long documentId) {
    AiDocument document = documentMapper.selectById(documentId);
    if (document != null && document.getAttachmentId() != null) {
        // 删除附件
        attachmentService.deleteAttachments(List.of(document.getAttachmentId()));
    }
    documentMapper.deleteById(documentId);
}
```

**查询流程**：
```java
public DocumentResponse getDocument(Long documentId) {
    AiDocument document = documentMapper.selectById(documentId);
    if (document == null) return null;

    // 关联查询附件信息
    SysAttachment attachment = null;
    if (document.getAttachmentId() != null) {
        attachment = attachmentService.getById(document.getAttachmentId());
    }
    return toDocumentResponse(document, attachment);
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
    response.setSummary(document.getSummary());
    response.setContent(document.getContent());
    response.setModelName(document.getModelName());
    response.setStatus(document.getStatus());
    response.setErrorMessage(document.getErrorMessage());
    response.setCreateTime(document.getCreateTime());
    response.setUpdateTime(document.getUpdateTime());
    return response;
}
```

#### 2.3 AiDocumentController (forge-module-ai-biz)

**新增摘要接口**：
```java
@Operation(summary = "生成文档摘要")
@GetMapping("/{id}/summary")
@PreAuthorize("hasAuthority('ai:document:analyze')")
public Result<DocumentResponse> summarizeDocument(@PathVariable Long id) {
    DocumentResponse response = aiDocumentService.generateSummary(id);
    return Result.success(response);
}
```

#### 2.4 AiDocumentService 摘要方法

```java
@Transactional
public DocumentResponse generateSummary(Long documentId) {
    AiDocument document = documentMapper.selectById(documentId);
    if (document == null) {
        throw new RuntimeException("文档不存在");
    }
    if (document.getContent() == null || document.getContent().isEmpty()) {
        throw new RuntimeException("文档内容为空，无法生成摘要");
    }

    // 获取默认模型配置
    AiModelConfig defaultModel = modelConfigService.getDefaultModel();
    if (defaultModel == null) {
        throw new RuntimeException("请先配置默认AI模型");
    }

    // 调用 Python 生成摘要
    DocumentSummaryRequest request = new DocumentSummaryRequest();
    request.setDocumentId(documentId);
    request.setModelName(defaultModel.getModelCode());
    request.setSummaryType("brief");

    DocumentResponse response = pythonAiClient.summarize(request);

    // 更新文档摘要
    if (response.getStatus() == 1) {
        document.setSummary(response.getSummary());
        document.setModelName(defaultModel.getModelCode());
        documentMapper.updateById(document);
    }

    // 返回完整文档信息（包含附件信息）
    SysAttachment attachment = document.getAttachmentId() != null
        ? attachmentService.getById(document.getAttachmentId()) : null;
    return toDocumentResponse(document, attachment);
}
```

### 3. 前端修改

#### 3.1 API 文件 (apps/forge-web/src/api/ai/document.ts)

```typescript
// 获取文档摘要 - 改为 GET 请求
summary: (id: number) =>
  request.get<DocumentResponse>(`/ai/document/${id}/summary`),
```

#### 3.2 页面调整 (apps/forge-web/src/views/ai/document/index.vue)

**摘要按钮点击**：
```typescript
const handleSummary = async (row: DocumentResponse) => {
  try {
    ElMessage.info('正在生成摘要...')
    const result = await getDocumentSummary(row.id)
    documentDetail.value = result
    detailTab.value = 'summary'
    detailDialogVisible.value = true
    ElMessage.success('摘要生成成功')
  } catch (e) {
    ElMessage.error('摘要生成失败')
  }
}
```

### 4. 错误处理

- 上传失败：记录错误信息到 errorMessage 字段，状态设置为 2
- 解析失败：保留文档记录和附件，用户可查看错误信息
- 摘要生成失败：抛出异常，前端显示错误提示

### 5. 测试要点

- 上传 PDF/Word/TXT 文件，验证附件表关联正确
- 删除文档时验证附件同步删除
- 摘要生成验证使用默认模型
- 文件详情查询验证附件信息显示正确
# AI文档管理功能修复实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复文档管理功能：文件上传集成系统附件服务，摘要生成功能正常工作

**Architecture:** 文件上传先存到附件表，再创建文档记录关联附件ID；摘要生成使用系统默认模型配置

**Tech Stack:** Spring Boot 3.2 + MyBatis Plus + Vue 3 + Element Plus

---

## 文件结构

| 文件 | 负责内容 |
|------|----------|
| `sql/migrate-ai-document.sql` | 数据库迁移：新增 attachment_id，移除冗余字段 |
| `AiDocument.java` | 实体修改：添加 attachmentId 字段 |
| `DocumentResponse.java` | DTO修改：添加 content 字段 |
| `AiModelService.java` | 接口新增：getDefaultModel 方法 |
| `AiModelServiceImpl.java` | 实现新增：getDefaultModel 方法 |
| `AiDocumentService.java` | 接口新增：generateSummary 方法 |
| `AiDocumentServiceImpl.java` | 核心修改：上传、删除、查询、摘要生成 |
| `AiDocumentController.java` | 控制器新增：GET 摘要接口 |
| `apps/forge-web/src/api/ai/document.ts` | API修改：摘要接口改为 GET |
| `apps/forge-web/src/views/ai/document/index.vue` | 页面修改：摘要按钮处理逻辑 |

---

### Task 1: 数据库迁移脚本

**Files:**
- Create: `sql/migrate-ai-document.sql`

- [ ] **Step 1: 创建迁移脚本**

```sql
-- ========================================
-- AI文档表结构迁移
-- 执行时间: 2026-06-17
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `forge_admin`;

-- 新增附件关联字段
ALTER TABLE `ai_document` ADD COLUMN `attachment_id` bigint DEFAULT NULL COMMENT '附件ID' AFTER `user_id`;
ALTER TABLE `ai_document` ADD KEY `idx_attachment_id` (`attachment_id`);

-- 移除冗余字段（文件信息通过附件表获取）
ALTER TABLE `ai_document` DROP COLUMN `file_path`;
ALTER TABLE `ai_document` DROP COLUMN `file_url`;
ALTER TABLE `ai_document` DROP COLUMN `file_size`;
ALTER TABLE `ai_document` DROP COLUMN `file_type`;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'AI文档表迁移完成!' AS message;
```

- [ ] **Step 2: 执行迁移脚本**

Run: `mysql -u root -p forge_admin < sql/migrate-ai-document.sql`
Expected: 输出 "AI文档表迁移完成!"

- [ ] **Step 3: 提交**

```bash
git add sql/migrate-ai-document.sql
git commit -m "feat(ai): 添加文档表迁移脚本，关联附件表"
```

---

### Task 2: 修改 AiDocument 实体

**Files:**
- Modify: `apps/forge-server/forge-module-ai/forge-module-ai-api/src/main/java/com/forge/modules/ai/entity/AiDocument.java`

- [ ] **Step 1: 修改实体类**

修改后的完整代码：

```java
package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 文档实体
 */
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

- [ ] **Step 2: 编译验证**

Run: `cd apps/forge-server && mvn compile -pl forge-module-ai/forge-module-ai-api -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-module-ai/forge-module-ai-api/src/main/java/com/forge/modules/ai/entity/AiDocument.java
git commit -m "feat(ai): AiDocument 实体添加 attachmentId 字段"
```

---

### Task 3: 修改 DocumentResponse DTO

**Files:**
- Modify: `apps/forge-server/forge-module-ai/forge-module-ai-api/src/main/java/com/forge/modules/ai/dto/response/DocumentResponse.java`

- [ ] **Step 1: 添加 content 字段**

修改后的完整代码：

```java
package com.forge.modules.ai.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档响应
 */
@Data
public class DocumentResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private String content;      // 新增：文档内容
    private String summary;
    private String modelName;
    private Integer status;  // 0-处理中 1-已完成 2-失败
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 编译验证**

Run: `cd apps/forge-server && mvn compile -pl forge-module-ai/forge-module-ai-api -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-module-ai/forge-module-ai-api/src/main/java/com/forge/modules/ai/dto/response/DocumentResponse.java
git commit -m "feat(ai): DocumentResponse 添加 content 字段"
```

---

### Task 4: AiModelService 接口新增 getDefaultModel 方法

**Files:**
- Modify: `apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/AiModelService.java`

- [ ] **Step 1: 添加方法声明**

在接口中添加：

```java
/**
 * 获取默认模型配置
 */
AiModelConfig getDefaultModel();
```

完整修改后的接口：

```java
package com.forge.modules.ai.service;

import com.forge.modules.ai.dto.response.ModelListResponse;
import com.forge.modules.ai.entity.AiModelConfig;

import java.util.List;

/**
 * AI模型服务接口
 */
public interface AiModelService {

    /**
     * 获取可用模型列表
     */
    ModelListResponse getAvailableModels();

    /**
     * 获取所有模型配置
     */
    List<AiModelConfig> getAllModelConfigs();

    /**
     * 获取模型配置详情
     */
    AiModelConfig getModelConfig(Long id);

    /**
     * 获取默认模型配置
     */
    AiModelConfig getDefaultModel();

    /**
     * 更新模型状态
     */
    void updateModelStatus(Long id, Integer status);

    /**
     * 设置默认模型
     */
    void setDefaultModel(Long id);

    /**
     * 刷新模型缓存
     */
    void refreshModelCache();

    /**
     * 更新模型配置
     */
    void updateModelConfig(Long id, AiModelConfig config);

    /**
     * 刷新单个模型状态
     */
    AiModelConfig refreshModelStatus(Long id);

    /**
     * 刷新所有模型状态
     */
    List<AiModelConfig> refreshAllModelStatus();

    /**
     * 新增模型配置
     */
    void addModelConfig(AiModelConfig config);

    /**
     * 删除模型配置
     */
    void deleteModelConfig(Long id);
}
```

- [ ] **Step 2: 编译验证**

Run: `cd apps/forge-server && mvn compile -pl forge-module-ai/forge-module-ai-biz -q`
Expected: 编译成功（此时实现类未更新，可能会有编译错误，需要下一步修复）

---

### Task 5: AiModelServiceImpl 实现 getDefaultModel 方法

**Files:**
- Modify: `apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/impl/AiModelServiceImpl.java`

- [ ] **Step 1: 添加实现方法**

在 AiModelServiceImpl 中添加：

```java
@Override
public AiModelConfig getDefaultModel() {
    LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(AiModelConfig::getIsDefault, 1);
    wrapper.eq(AiModelConfig::getStatus, 1);
    wrapper.last("LIMIT 1");
    return modelConfigMapper.selectOne(wrapper);
}
```

- [ ] **Step 2: 编译验证**

Run: `cd apps/forge-server && mvn compile -pl forge-module-ai/forge-module-ai-biz -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/AiModelService.java apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/impl/AiModelServiceImpl.java
git commit -m "feat(ai): AiModelService 添加 getDefaultModel 方法"
```

---

### Task 6: AiDocumentService 接口新增 generateSummary 方法

**Files:**
- Modify: `apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/AiDocumentService.java`

- [ ] **Step 1: 添加方法声明**

完整修改后的接口：

```java
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
     * 生成文档摘要（使用默认模型）
     */
    DocumentResponse generateSummary(Long documentId);

    /**
     * 生成文档摘要（指定模型）
     */
    DocumentResponse summarize(DocumentSummaryRequest request);

    /**
     * 获取文档列表（不分页）
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
```

- [ ] **Step 2: 编译验证**

Run: `cd apps/forge-server && mvn compile -pl forge-module-ai/forge-module-ai-biz -q`
Expected: 编译成功（实现类未更新，下一步修复）

---

### Task 7: AiDocumentServiceImpl 核心修改

**Files:**
- Modify: `apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/impl/AiDocumentServiceImpl.java`

- [ ] **Step 1: 添加依赖注入**

在类顶部添加：

```java
private final SysAttachmentService attachmentService;
private final AiModelService modelService;
```

修改构造函数（使用 @RequiredArgsConstructor 自动生成）：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentServiceImpl implements AiDocumentService {

    private final PythonAiClient pythonAiClient;
    private final AiDocumentMapper documentMapper;
    private final SysAttachmentService attachmentService;  // 新增
    private final AiModelService modelService;              // 新增

    // ... 其他方法
}
```

- [ ] **Step 2: 修改上传流程**

修改 parseDocumentFile 方法：

```java
@Override
@Transactional
public DocumentResponse parseDocumentFile(Long documentId, MultipartFile file) {
    AiDocument document = null;

    // 如果没有 documentId，先上传附件并创建文档记录
    if (documentId == null) {
        // 1. 上传到附件服务
        AttachmentResponse attachment = attachmentService.upload(file, "ai_document", null);

        // 2. 创建文档记录
        document = new AiDocument();
        document.setUserId(SecurityHelper.getCurrentUserId());
        document.setAttachmentId(attachment.getId());
        document.setFileName(attachment.getOriginalName());
        document.setStatus(0);  // 处理中
        documentMapper.insert(document);
        documentId = document.getId();

        // 3. 调用 Python 解析文档内容
        DocumentResponse response = pythonAiClient.parseDocument(documentId, attachment.getFilePath());

        // 4. 更新文档状态
        document.setStatus(response.getStatus() != null && response.getStatus() == 1 ? 1 : 2);
        document.setContent(response.getText());
        if (response.getErrorMessage() != null) {
            document.setErrorMessage(response.getErrorMessage());
        }
        documentMapper.updateById(document);
    } else {
        document = documentMapper.selectById(documentId);
        if (document != null) {
            document.setStatus(0);
            documentMapper.updateById(document);
        }
    }

    // 返回数据库中的文档信息（包含附件信息）
    SysAttachment attachment = document.getAttachmentId() != null
        ? attachmentService.getById(document.getAttachmentId()) : null;
    return document != null ? toDocumentResponse(document, attachment) : null;
}
```

- [ ] **Step 3: 修改删除流程**

修改 deleteDocument 方法：

```java
@Override
@Transactional
public void deleteDocument(Long documentId) {
    AiDocument document = documentMapper.selectById(documentId);
    if (document != null) {
        // 删除关联的附件
        if (document.getAttachmentId() != null) {
            attachmentService.deleteAttachments(List.of(document.getAttachmentId()));
        }
        documentMapper.deleteById(documentId);
    }
}
```

- [ ] **Step 4: 修改查询流程**

修改 getDocument 方法：

```java
@Override
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
```

修改 toDocumentResponse 方法：

```java
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
```

- [ ] **Step 5: 实现 generateSummary 方法**

添加新方法：

```java
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

    // 获取默认模型配置
    AiModelConfig defaultModel = modelService.getDefaultModel();
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
    if (response != null && response.getStatus() != null && response.getStatus() == 1) {
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

- [ ] **Step 6: 修改 pageDocument 方法中的 toDocumentResponse 调用**

修改分页查询中的转换：

```java
@Override
public IPage<DocumentResponse> pageDocument(DocumentQueryRequest request) {
    Page<AiDocument> page = new Page<>(request.getPageNum(), request.getPageSize());
    LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<>();

    // 文件名模糊查询
    if (request.getFileName() != null && !request.getFileName().isEmpty()) {
        wrapper.like(AiDocument::getFileName, request.getFileName());
    }

    // 状态查询
    if (request.getStatus() != null) {
        wrapper.eq(AiDocument::getStatus, request.getStatus());
    }

    // 用户ID查询（普通用户只能查看自己的文档）
    Long queryUserId = request.getUserId();
    if (queryUserId == null && !SecurityHelper.isAdmin()) {
        queryUserId = SecurityHelper.getCurrentUserId();
    }
    if (queryUserId != null) {
        wrapper.eq(AiDocument::getUserId, queryUserId);
    }

    // 按创建时间倒序
    wrapper.orderByDesc(AiDocument::getCreateTime);

    Page<AiDocument> result = documentMapper.selectPage(page, wrapper);

    // 转换时关联附件信息
    return result.convert(doc -> {
        SysAttachment attachment = doc.getAttachmentId() != null
            ? attachmentService.getById(doc.getAttachmentId()) : null;
        return toDocumentResponse(doc, attachment);
    });
}
```

- [ ] **Step 7: 完整的 AiDocumentServiceImpl**

完整代码：

```java
package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.ai.client.PythonAiClient;
import com.forge.modules.ai.dto.request.DocumentQueryRequest;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.AttachmentResponse;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.entity.AiDocument;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiDocumentMapper;
import com.forge.modules.ai.service.AiDocumentService;
import com.forge.modules.ai.service.AiModelService;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.SysAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
        AiDocument document = null;
        if (documentId != null) {
            document = documentMapper.selectById(documentId);
            if (document != null) {
                document.setStatus(0);
                documentMapper.updateById(document);
            }
        }

        DocumentResponse response = pythonAiClient.parseDocument(documentId, filePath);

        if (document != null && response != null) {
            document.setStatus(response.getStatus() == 1 ? 1 : 2);
            document.setContent(response.getText());
            document.setSummary(response.getSummary());
            document.setModelName(response.getModelName());
            documentMapper.updateById(document);
        }

        SysAttachment attachment = document != null && document.getAttachmentId() != null
            ? attachmentService.getById(document.getAttachmentId()) : null;
        return document != null ? toDocumentResponse(document, attachment) : response;
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

            DocumentResponse response = pythonAiClient.parseDocument(documentId, attachment.getFilePath());

            document.setStatus(response.getStatus() != null && response.getStatus() == 1 ? 1 : 2);
            document.setContent(response.getText());
            if (response.getErrorMessage() != null) {
                document.setErrorMessage(response.getErrorMessage());
            }
            documentMapper.updateById(document);
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

        DocumentSummaryRequest request = new DocumentSummaryRequest();
        request.setDocumentId(documentId);
        request.setModelName(defaultModel.getModelCode());
        request.setSummaryType("brief");

        DocumentResponse response = pythonAiClient.summarize(request);

        if (response != null && response.getStatus() != null && response.getStatus() == 1) {
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
        DocumentResponse response = pythonAiClient.summarize(request);

        if (response != null && response.getStatus() != null && response.getStatus() == 1) {
            AiDocument document = documentMapper.selectById(request.getDocumentId());
            if (document != null) {
                document.setSummary(response.getSummary());
                document.setModelName(request.getModelName());
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
```

- [ ] **Step 8: 编译验证**

Run: `cd apps/forge-server && mvn compile -pl forge-module-ai/forge-module-ai-biz -q`
Expected: 编译成功

- [ ] **Step 9: 提交**

```bash
git add apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/AiDocumentService.java apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/service/impl/AiDocumentServiceImpl.java
git commit -m "feat(ai): AiDocumentService 集成附件服务，新增 generateSummary 方法"
```

---

### Task 8: AiDocumentController 新增 GET 摘要接口

**Files:**
- Modify: `apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/controller/admin/AiDocumentController.java`

- [ ] **Step 1: 添加 GET 摘要接口**

完整修改后的控制器：

```java
package com.forge.modules.ai.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.request.DocumentQueryRequest;
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
        DocumentResponse response = aiDocumentService.parseDocumentFile(null, file);
        return Result.success(response);
    }

    @Operation(summary = "分页查询文档列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ai:document:query')")
    public Result<PageResult<DocumentResponse>> getDocumentList(DocumentQueryRequest request) {
        IPage<DocumentResponse> page = aiDocumentService.pageDocument(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:document:query')")
    public Result<DocumentResponse> getDocument(@PathVariable Long id) {
        DocumentResponse response = aiDocumentService.getDocument(id);
        return Result.success(response);
    }

    @Operation(summary = "生成文档摘要")
    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAuthority('ai:document:analyze')")
    @OperationLog(title = "AI文档管理", businessType = OperationLog.BusinessType.OTHER)
    public Result<DocumentResponse> summarizeDocument(@PathVariable Long id) {
        DocumentResponse response = aiDocumentService.generateSummary(id);
        return Result.success(response);
    }

    @Operation(summary = "生成文档摘要（指定模型）")
    @PostMapping("/summary")
    @PreAuthorize("hasAuthority('ai:document:analyze')")
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
```

- [ ] **Step 2: 编译验证**

Run: `cd apps/forge-server && mvn compile -pl forge-module-ai/forge-module-ai-biz -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-module-ai/forge-module-ai-biz/src/main/java/com/forge/modules/ai/controller/admin/AiDocumentController.java
git commit -m "feat(ai): AiDocumentController 新增 GET 方式摘要接口"
```

---

### Task 9: 前端 API 修改

**Files:**
- Modify: `apps/forge-web/src/api/ai/document.ts`

- [ ] **Step 1: 修改摘要接口**

修改后的完整代码：

```typescript
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

// 文档响应
export interface DocumentResponse {
  id: number
  fileName: string
  fileSize: number
  fileType: string
  fileUrl: string
  content: string | null  // 新增
  status: number // 0: 处理中, 1: 已完成, 2: 处理失败
  summary: string | null
  modelName: string | null
  errorMessage: string | null
  createTime: string
  updateTime: string
}

// 文档查询参数
export interface DocumentQuery {
  fileName?: string
  fileType?: string
  status?: number
  createTimeStart?: string
  createTimeEnd?: string
  pageNum: number
  pageSize: number
}

// 文档上传响应
export interface DocumentUploadResponse {
  id: number
  fileName: string
  fileSize: number
  fileType: string
  status: number
}

export const documentApi = {
  // 上传文档
  upload: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<DocumentUploadResponse>('/ai/document/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 获取文档列表
  list: (params: DocumentQuery) =>
    request.get<PageResult<DocumentResponse>>('/ai/document/list', { params }),

  // 获取文档详情
  get: (id: number) =>
    request.get<DocumentResponse>(`/ai/document/${id}`),

  // 获取文档摘要 - 改为 GET 请求，使用默认模型
  summary: (id: number) =>
    request.get<DocumentResponse>(`/ai/document/${id}/summary`),

  // 删除文档
  delete: (id: number) =>
    request.delete(`/ai/document/${id}`),

  // 批量删除文档
  batchDelete: (ids: number[]) =>
    request.delete('/ai/document/batch', { data: ids })
}

// 导出独立函数
export const uploadDocument = (file: File) =>
  documentApi.upload(file).then(res => res.data)
export const getDocumentList = (params: DocumentQuery) =>
  documentApi.list(params).then(res => res.data)
export const getDocumentDetail = (id: number) =>
  documentApi.get(id).then(res => res.data)
export const getDocumentSummary = (id: number) =>
  documentApi.summary(id).then(res => res.data)
export const deleteDocument = (id: number) => documentApi.delete(id)
export const batchDeleteDocuments = (ids: number[]) => documentApi.batchDelete(ids)
```

- [ ] **Step 2: 提交**

```bash
git add apps/forge-web/src/api/ai/document.ts
git commit -m "feat(ai): 前端 API 摘要接口改为 GET 请求"
```

---

### Task 10: 前端页面修改

**Files:**
- Modify: `apps/forge-web/src/views/ai/document/index.vue`

- [ ] **Step 1: 修改摘要按钮处理逻辑**

修改 handleSummary 函数：

```typescript
const handleSummary = async (row: DocumentResponse) => {
  try {
    ElMessage.info('正在生成摘要...')
    const result = await getDocumentSummary(row.id)
    documentDetail.value = result
    detailTab.value = 'summary'
    detailDialogVisible.value = true
    ElMessage.success('摘要生成成功')
    getList()  // 刷新列表显示摘要状态
  } catch (e) {
    ElMessage.error('摘要生成失败')
  }
}
```

完整修改后的 script 部分：

```typescript
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getDocumentList, getDocumentDetail, getDocumentSummary, uploadDocument, deleteDocument } from '@/api/ai/document'
import type { DocumentResponse, DocumentQuery } from '@/api/ai/document'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const uploadRef = ref()

const queryParams = reactive<DocumentQuery>({
  fileName: '',
  fileType: '',
  status: undefined,
  pageNum: 1,
  pageSize: 20
})

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

const loading = ref(false)
const tableData = ref<DocumentResponse[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const detailDialogVisible = ref(false)
const detailTab = ref('content')
const documentDetail = ref<DocumentResponse | null>(null)

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getDocumentList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.fileName = ''
  queryParams.fileType = ''
  queryParams.status = undefined
  handleQuery()
}

const beforeUpload = (file: File) => {
  const allowedTypes = ['pdf', 'docx', 'doc', 'txt']
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !allowedTypes.includes(ext)) {
    ElMessage.error('只支持 PDF、Word、TXT 格式')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

const handleUpload = async (options: any) => {
  try {
    await uploadDocument(options.file)
    ElMessage.success('上传成功')
    getList()
  } catch (e) {
    ElMessage.error('上传失败')
  }
}

const handleDetail = async (row: DocumentResponse) => {
  documentDetail.value = await getDocumentDetail(row.id)
  detailDialogVisible.value = true
}

const handleSummary = async (row: DocumentResponse) => {
  try {
    ElMessage.info('正在生成摘要...')
    const result = await getDocumentSummary(row.id)
    documentDetail.value = result
    detailTab.value = 'summary'
    detailDialogVisible.value = true
    ElMessage.success('摘要生成成功')
    getList()
  } catch (e) {
    ElMessage.error('摘要生成失败')
  }
}

const handleDelete = (row: DocumentResponse) => {
  ElMessageBox.confirm('确定要删除该文档吗？', '提示', { type: 'warning' }).then(async () => {
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    getList()
  })
}

const handleCheckboxChange = () => {
  const records = tableRef.value?.getCheckboxRecords() || []
  selectedIds.value = records.map((r: DocumentResponse) => r.id)
}

const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}
</script>
```

- [ ] **Step 2: 提交**

```bash
git add apps/forge-web/src/views/ai/document/index.vue
git commit -m "feat(ai): 前端文档页面摘要功能修改"
```

---

### Task 11: 整体编译验证

- [ ] **Step 1: 编译整个后端项目**

Run: `cd apps/forge-server && mvn clean compile -DskipTests -q`
Expected: 编译成功

- [ ] **Step 2: 编译前端项目**

Run: `cd apps/forge-web && pnpm build`
Expected: 构建成功

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat(ai): 文档管理功能修复完成"
```
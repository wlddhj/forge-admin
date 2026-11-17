# 附件管理与服务全面分析报告

> 2026-11-17 · 范围：后端 forge-module-system 及 6 个使用方模块、前端 forge-web / forge-miniapp、SQL 脚本
> 结论速览：存储 SPI 骨架是好的（所有落盘统一走 `FileStorageClient`），但存在 **1 个安装即坏的 P0**、**1 个编辑即毁配置的 P0**、**SPI 缺读能力逼出 AI 模块直读本地盘** 等 16 项问题，建议按 A→B→C→D 四批演进，E 批按部署形态选做。

---

## 1. 现状全景

### 1.1 数据模型

| 表 | 租户 | 说明 |
|---|---|---|
| `sys_attachment` | **租户隔离**（tenant_id 自动注入，init.sql:853 补列） | 14 列 + idx_biz(biz_type,biz_id)；物理删除、无 update_time |
| `sys_file_config` | **全局共享**（tenant ignore-tables） | 4 种存储 local/aliyun_oss/tencent_cos/minio，单一"默认配置"供所有上传 |

### 1.2 核心链路（后端，均在 forge-module-system）

```
SysAttachmentController /system/attachment/**     ─┐
AppAttachmentController /app-api/attachment/**     ├─→ SysAttachmentServiceImpl.upload()
SysBrandServiceImpl:98 (logo)                      │     ├─ FileUploadValidator（大小/白名单/Magic Number）
ContractServiceImpl:293 (定稿PDF)                  │     ├─ SysFileConfigService.getDefaultConfig()（@Cacheable）
AiDocumentServiceImpl:114 (AI文档)                 │     ├─ key = yyyy/MM/dd/uuid.ext
WfTaskServiceImpl:725 (审批评论,lambdaUpdate回绑)  │     └─ FileStorageClient.upload() × 4 实现
SysNoticeServiceImpl:145 (公告,lambdaUpdate回绑)   ─┘          Local / AliyunOss / TencentCos / Minio
```

读取侧仅两种形态：
- **公开直链**：local → `UploadsController /uploads/**`（permitAll + 租户闸放行，UUID 防枚举）；云存储 → `domain + key`（domain 空时 Minio 拼 `endpoint/bucket/key`，**要求桶公共读**）
- **本地直读**：仅 AI 文档解析 `AiDocumentServiceImpl.readLocalFile(filePath)`（:250-267）

### 1.3 biz_type 写入分布（7 种，无枚举）

| 值 | 写入方 | bizId | 风格 |
|---|---|---|---|
| `avatar` | SysAttachmentController:82 | null | 小写 |
| `APP_AVATAR` | AppAttachmentController:43 | null | 大写 |
| `brand-logo` | SysBrandServiceImpl:98 | null | kebab |
| `ai_document` | AiDocumentServiceImpl:114 | null（ai_document 表另存 attachmentId） | snake |
| `contract` | ContractServiceImpl:293 | 合同id | 小写 |
| `contract_template` | TemplateEditor.vue:541 | **恒 null** | snake |
| `wf_comment` / `notice` | 前端上传+后端 lambdaUpdate 回绑 | 评论/公告id | snake |

### 1.4 前端上传姿势（4 套）

1. `FileUpload.vue` 组件（v-model=attachmentIds）：notice、TaskDetailDrawer
2. 裸 `el-upload` + `uploadAttachment()`：attachment 管理页、TemplateEditor
3. 专用端点：avatar（profile、app-user）、brand logo、ai document
4. 自有端点（不落附件表，合理）：user import、plugin license import

miniapp：`uni.uploadFile → /app-api/attachment/upload`（仅头像）。

---

## 2. 架构做对了什么（保留项）

- **存储策略 SPI 收口彻底**：全仓无业务私自写盘，唯一写 `uploads/` 的就是 LocalStorageClient；MinioStorageClient 的"构造指纹 + 原子 compute + evict"缓存设计正确处理了配置变更重建。
- **上传统一走 `SysAttachmentService.upload()`**：校验、key 生成、落库一处收口，跨模块复用而非各写各的。
- **FileUploadValidator** 有扩展名白名单 + Magic Number 双校验（yml 可配）。
- **租户模型清晰**：附件行隔离、存储配置全局共享，口径自洽。
- 历史附件删除的 `resolveStorageConfig` 回退匹配（fileConfigId→桶名前缀）考虑了存量数据。

---

## 3. 问题清单

### P0 — 正确性（安装即坏 / 编辑即毁）

**1. `sys_attachment.file_config_id` 列从未进过任何 SQL。**
实体有该字段（SysAttachment.java:47）、upload 写入（ServiceImpl:168）、删除时依赖（:217），但 `init.sql` 建表段和全部 `sql/upgrade-*.sql` 均无此列（git 全历史 `-S file_config_id` 为空）。dev 库是当时手工补的列（对应记忆中 V2026101201 事件，脚本未沉淀）。**全新部署第一次上传即报 Unknown column。** 连带问题：tenant_id 靠 init.sql 末尾 ALTER 补（:853）而非建表内联，风格上易再犯同类遗漏。

**2. 编辑文件存储配置会毁掉密钥（minio/oss 必炸，local 无感）。**
链路：`FileConfigResponse` 仅回传脱敏 `accessKey=ABCD****`（ServiceImpl:277-282）、`secretKey` 字段被注释不回传 → 前端 `handleEdit` 用 `Object.assign(formData, row)` 回填（file-config/index.vue:355）→ 提交时 accessKey=掩码串、secretKey='' → 后端 `BeanUtil.copyProperties(request, config)`（SysFileConfigServiceImpl:100）全量覆盖。**结果：任何一次编辑保存后，accessKey 变成 `ABCD****`、secretKey 变成空**，该配置下次上传即失败。默认种子是 local 所以从未暴露。

### P1 — 架构缺陷

**3. `FileStorageClient` SPI 缺"读"能力，逼出 AI 模块直读本地盘。**
接口只有 upload/delete（FileStorageClient.java:24-32）。AI 文档解析需要文件字节，只能 `readLocalFile(attachment.getFilePath())` 硬读文件系统（AiDocumentServiceImpl:267）——**默认存储切到 minio/oss 后 AI 文档解析直接不可用**，租户/部署形态升级的隐性炸弹。

**4. `MultipartFile`（web 层类型）泄漏进服务层与 SPI 签名。**
Contract 定稿为复用统一上传被迫自造 `InMemoryMultipartFile` 适配器。SPI 应面向 `InputStream/byte[]`，web 适配应在 controller 层完成。

**5. 孤儿附件无任何回收机制。**
上传时 bizId=null、事后回绑的模式下：用户放弃表单、业务删除（AI 文档外的合同/公告/评论删了不删附件）、TemplateEditor 图片恒 null——行与物理文件永久滞留。无清理 Job、无引用计数、无"未绑定超期"概念。

**6. 附件删除无引用校验 + 物理删无审计。**
附件管理页可删任何附件（deleteAttachments 直接删行删文件），被合同定稿 PDF/公告/审批评论引用中的文件照删，业务侧随后 404。sys_attachment 无 @TableLogic、无 update_time，删了无痕。

**7. 访问控制只有"永久公开直链"一种形态。**
无下载代理端点、无预签名。私有桶（minio/oss 常见安全基线）部署下所有预览直接不可用；local 的 `/uploads/**` permitAll 依赖 UUID 不可枚举兜底（已按此口径放行租户闸，自洽但要求所有业务永久接受"附件 URL 即凭证"模型）。

### P2 — 一致性 / 卫生

8. **bizType 无枚举、三种命名风格混用**（见 1.3 表），散落 6 个模块靠字符串对齐，拼错即静默失联。
9. **App 通道校验口径分叉**：AppAttachmentController 硬编码 2MB/3 类型（:33-53），与 UploadPolicyProperties 全局策略两套账；再做 `/api/uploads/`→`/uploads/` 字符串改写（:61-63）这种补丁胶水。
10. **头像/图片端点三入口两权限模型**：`/avatar` 无 @PreAuthorize（登录即传，语义合理但未显式声明依据）、`/upload` 要 `system:attachment:upload` 权限、`/brand/logo` 走 service 无独立端点。普通用户发公告/审批评论需被授予 `system:attachment:upload`，权限运营上易漏。
11. **SysAttachmentServiceImpl 全程 RuntimeException**（:101,119,125,142,243,251），项目已有 BusinessException + 全局异常处理，语义分叉、HTTP 状态码被拉平。
12. **`list-by-biz` 无权限注解**（SysAttachmentController:50-53）：租户内任意登录用户可枚举任意业务的附件元数据（fileUrl 本就公开，实害低，但元数据含上传人）。
13. **`fileConfig` 缓存未列 ignore-caches**：全局表数据被按租户前缀各缓存一份，与 sysConfig/menu/dict 的处理不一致（无害，浪费）。
14. **前端策略双份漂移**：`constants/upload.ts` 与后端 UploadPolicyProperties 各自维护大小/类型限制；上传组件四套姿势并存（见 1.4）。
15. **deleteAttachments 逐条 selectById+deleteById**（N+1，量小无实害）。
16. **UploadsController 边缘**：默认配置切非 local 后回退 `./uploads`，此前自定义 basePath 的存量文件直链断（边缘场景）。

---

## 4. 优化方案（分批，A→B→C→D 必做递进，E 选做）

**总原则**：不动表主体结构、存量 fileUrl 必须继续可访问（兼容旧直链）、增量演进不搞大爆炸。

### 批次 A：正确性止血（估 0.5~1 天）

| # | 内容 |
|---|---|
| A1 | `sys/init.sql` 建表段内联 `file_config_id bigint NULL` + `KEY idx_file_config(file_config_id)`（顺带把 tenant_id 内联进建表段，删尾部 ALTER）；新增 `sql/upgrade-20261117-attachment-file-config-id.sql`（可空列 + 按 storage_type 回填存量行匹配的配置 id） |
| A2 | 修密钥回写：后端 `updateFileConfig` 对 accessKey/secretKey 为空或含 `****` 时跳过覆盖（保留库中原值）；前端编辑态两个输入框 placeholder 改"留空则不修改" |
| A3 | SysAttachmentServiceImpl 的 RuntimeException 全量换 BusinessException |

### 批次 B：SPI 补全，解除 AI 硬耦合（估 1~2 天，架构收益最大）

| # | 内容 |
|---|---|
| B1 | `FileStorageClient` 增加 `InputStream download(SysFileConfig, String filePath)`：local=Files.newInputStream、minio=getObject、oss/cos=getObject |
| B2 | `SysAttachmentService` 增加 `byte[] loadContent(Long attachmentId)`：按行内 storage_type+file_config_id 定位配置统一读取 |
| B3 | SPI 上传签名去 MultipartFile 化：`upload(config, InputStream in, long size, String contentType, String key)`；MultipartFile→流的适配收在 SysAttachmentServiceImpl；删除 InMemoryMultipartFile |
| B4 | AiDocumentServiceImpl.readLocalFile 改走 loadContent，fallbackPath 旧路径保留为二级回退 |

### 批次 C：口径统一（估 1 天）

| # | 内容 |
|---|---|
| C1 | system-api 新增 `AttachmentBizTypes` 常量类（AVATAR/BRAND_LOGO/AI_DOCUMENT/CONTRACT/CONTRACT_TEMPLATE/WF_COMMENT/NOTICE），6 个模块全部改引用；存量 biz_type 归一 UPDATE（APP_AVATAR→avatar、brand-logo→brand_logo）随升级 SQL |
| C2 | AppAttachmentController 删自带校验改用 FileUploadValidator（如需收紧头像场景，给 UploadPolicyProperties 加 per-scene 覆盖而非硬编码）；`/api/uploads/` 改写逻辑移至 StorageResult URL 生成处统一处理 |
| C3 | 端点收敛：保留 `/avatar`（补注释说明"登录即传"的权限语义）；brand logo 与通用 upload 并轨评估（改走 upload(bizType=brand_logo) 可少一个入口） |
| C4 | `list-by-biz` 补权限注解（至少显式 isAuthenticated，或跟 attachment:query） |
| C5 | `fileConfig` 加入 tenant ignore-caches |

### 批次 D：生命周期治理（估 2~3 天，可独立排期）

| # | 内容 |
|---|---|
| D1 | 删除防护：定义 `AttachmentRefChecker` SPI，各业务模块注册（contract 查定稿关联、notice/wf_comment 查绑定行）；deleteAttachments 对"被引用"附件默认拒绝或仅解绑不删物理文件；附件管理页删除弹窗分级提示 |
| D2 | 孤儿回收：sys_job 定时任务回收 `biz_id IS NULL AND create_time < now()-7d`（contract_template 图片改为模板保存时回绑 bizId 后即可纳入回收） |
| D3 | （可选，需用户拍板）sys_attachment 加 deleted 软删 + update_time——遵循项目 A1 规范（唯一索引含 deleted 列才需要 delval 方案，本表唯一索引是主键，普通 @TableLogic 即可） |

### 批次 E：访问控制升级（选做，按部署形态）

| # | 内容 | 适用 |
|---|---|---|
| E1 | 下载代理 `/system/attachment/{id}/download`：鉴权 + Content-Disposition，私有桶/审计场景兜底 | 任意部署 |
| E2 | `getPresignedUrl(config, key, ttl)`：fileUrl 存相对 key、展示时签名 | 新部署或愿意做数据迁移的私有桶场景 |
| E3 | per-biz 存储路由（不同 bizType 走不同配置） | 有分级存储诉求时 |

### 不建议做的

- **不建议**重构成独立 file 微服务/引入 Spring Resource 抽象层——现有 SPI 粒度对单体多模块刚刚好，收益不抵复杂度。
- **不建议**给 sys_file_config 加租户维度——当前"配置全局、附件行隔离"模型自洽，除非出现"租户自带存储"的真实需求。

---

## 5. 执行建议

A 批是纯止血，随时可做；B 批做完 AI 模块的 local 硬耦合即解除，是切 minio/oss 前置条件；C/D 可合并为一个迭代；E 在确定生产存储形态（公有桶 or 私有桶）后再定。实施属重任务，建议立 `attachment-refactor` 任务走完整流程，A/B/C/D 分四个 PR 交付。

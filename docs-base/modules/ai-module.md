# AI 模块（forge-module-ai）使用手册

> 适用范围：`apps/forge-server/forge-module-ai`（后端）与 `apps/forge-web/src/views/ai`（前端管理页）。
>
> AI 能力全部在 Java 端实现（基于 Spring AI 1.1.8，无独立 AI 服务进程），四个 provider（deepseek/qwen/glm/ernie）统一走 OpenAI 兼容协议直连上游。

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 架构设计](#2-架构设计)
- [3. 前端页面](#3-前端页面)
- [4. API 端点](#4-api-端点)
- [5. 配置项](#5-配置项)
- [6. 数据库表结构](#6-数据库表结构)
- [7. 模型配置指南](#7-模型配置指南)
- [8. 安全注意事项](#8-安全注意事项)
- [9. 故障排查](#9-故障排查)
- [10. 测试运行说明](#10-测试运行说明)

---

## 1. 模块概述

| 能力 | 说明 |
|------|------|
| 多模型对话 | 非流式（同步返回）+ SSE 流式两种模式，支持按 modelId / modelName 选择模型 |
| 文档解析 | 上传 pdf/docx/txt，基于 Apache Tika 提取文本内容 |
| 文档摘要 | 调用 LLM 生成 brief / detailed / bullet 三种风格摘要 |
| 模型配置管理 | `ai_model_config` 表为唯一配置源（端点/密钥/温度/maxTokens），页面可增删改、启停、切换默认模型、探测可用性 |
| 用量统计 | `ai_api_usage` 表按用户/模型/日维度记录请求量、token 消耗与费用 |

**支持的模型提供商（`ModelProvider` 枚举）：**

| code | 名称 |
|------|------|
| `deepseek` | DeepSeek |
| `qwen` | 通义千问 |
| `glm` | 智谱GLM |
| `ernie` | 文心一言 |

---

## 2. 架构设计

### 2.1 模块结构

```
forge-module-ai-api/                    # 实体 + DTO + 枚举
├── entity/                             # AiModelConfig / AiConversation / AiMessage / AiDocument / AiApiUsage
├── dto/request/                        # ChatRequest / CreateConversationRequest / DocumentQueryRequest 等
├── dto/response/                       # ChatResponse / MessageResponse / ModelListResponse 等
└── enums/                              # ModelProvider / MessageRole / DocumentStatus

forge-module-ai-biz/                    # 业务实现
├── client/                             # LLM 客户端层
│   ├── LlmClient                       # 门面接口：chat 非流式 + stream 流式
│   ├── SpringAiLlmClient               # 基于 Spring AI 的实现
│   ├── OpenAiChatModelFactory          # 按 ai_model_config 编程式构建并缓存 OpenAiChatModel
│   └── ModelHealthProbe                # 模型可用性探测（max_tokens=1）
├── service/
│   ├── AiModelResolver                 # 模型解析：modelId → modelName/modelCode → 默认模型
│   ├── TikaDocumentParser              # 文档解析（pdf/docx/txt）
│   ├── DocumentSummarizer              # 摘要生成（三种风格 prompt 模板）
│   └── impl/                           # AiChatServiceImpl / AiDocumentServiceImpl / AiModelServiceImpl
├── config/
│   ├── AiModuleConfig                  # @ConfigurationProperties(prefix = "ai")
│   └── AiClientConfig                  # 上游 WebClient（带超时，16MB 流式缓冲）
└── controller/admin/                   # AiChatController / AiModelController / AiDocumentController
```

### 2.2 LLM 调用链路

```
Controller → AiModelResolver.resolve(request)   # 解析出 AiModelConfig
           → LlmClient.chat / stream            # 门面调用
           → OpenAiChatModelFactory.get(config) # 获取（或构建）模型实例
           → 上游 OpenAI 兼容端点（/chat/completions）
```

### 2.3 模型解析回退（AiModelResolver）

请求未显式指定可用模型时逐级回退：

1. `request.modelId` 精确匹配（须 `status=1`）
2. `request.modelName`（或配置的默认模型编码）按 `modelName`/`modelCode` 匹配启用模型
3. 默认模型（`isDefault=1` 且启用）→ 任一启用模型 → 无可用模型返回 `null`

### 2.4 模型实例缓存与重建（OpenAiChatModelFactory）

- 实例缓存在 `ConcurrentHashMap<modelConfigId, CachedModel>`，以**配置指纹**（endpoint/apiKey/modelCode/温度/maxTokens 等）标识
- 调用 `get(config)` 时指纹一致直接复用，指纹变化自动重建；配置更新后调用 `evict(modelConfigId)` 主动失效
- 因此**修改 `ai_model_config` 后无需重启应用**，模型实例按需重建

### 2.5 流式对话分工（重要）

```
POST /admin-api/ai/chat/message/stream   # 只发 SSE 流，不落库 AI 回复
     │  （服务端先在同步线程 prepareStreamChat 保存用户消息，需要 Security Context）
     ▼
前端接收 SSE 帧：{"content":"…"} 增量 → [DONE] 结束（{"error":true,"message":"…"} 异常）
     │
     ▼
POST /admin-api/ai/chat/message/save-ai  # 前端收完后保存 AI 回复
```

---

## 3. 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| AI 对话 | `views/ai/chat` | 对话窗口：发送消息（流式渲染）、模型切换、消息历史 |
| 会话管理 | `views/ai/conversation` | 对话列表：分页查询、改名、删除 |
| 文档管理 | `views/ai/document` | 文档上传（pdf/docx/txt）、内容查看、摘要生成 |
| 模型管理 | `views/ai/model` | 模型配置：新增/编辑、启停、默认模型切换、状态探测刷新 |

对应 API 定义：`src/api/ai/chat.ts`、`src/api/ai/model.ts`、`src/api/ai/document.ts`。

---

## 4. API 端点

前缀 `/admin-api`，权限码格式 `ai:{entity}:{action}`。

### 4.1 AI 对话（AiChatController，9 个）

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| POST | `/ai/chat/conversation` | `ai:chat:create` | 创建新对话 |
| GET | `/ai/chat/conversation/list` | `ai:chat:query` | 对话列表（分页，可按 modelId 过滤） |
| GET | `/ai/chat/conversation/{id}` | `ai:chat:query` | 对话详情 |
| GET | `/ai/chat/conversation/{id}/messages` | `ai:chat:query` | 对话消息列表 |
| PUT | `/ai/chat/conversation/{id}/title` | `ai:chat:edit` | 更新对话标题 |
| DELETE | `/ai/chat/conversation/{id}` | `ai:chat:delete` | 删除对话 |
| POST | `/ai/chat/message` | `ai:chat:create` | 发送消息（非流式） |
| POST | `/ai/chat/message/stream` | `ai:chat:create` | 发送消息（SSE 流式，`text/event-stream`） |
| POST | `/ai/chat/message/save-ai` | `ai:chat:create` | 保存 AI 回复消息 |

### 4.2 模型配置（AiModelController，11 个）

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/ai/model/available` | `ai:model:query` | 可用模型列表 |
| GET | `/ai/model/list` | `ai:model:query` | 所有模型配置 |
| GET | `/ai/model/{id}` | `ai:model:query` | 模型配置详情 |
| POST | `/ai/model` | `ai:model:add` | 新增模型配置 |
| PUT | `/ai/model/{id}/config` | `ai:model:config` | 配置模型（端点/密钥/参数） |
| PUT | `/ai/model/{id}/status` | `ai:model:config` | 更新模型启停状态 |
| PUT | `/ai/model/{id}/default` | `ai:model:switch` | 切换默认模型 |
| POST | `/ai/model/refresh` | `ai:model:config` | 刷新模型缓存（主动失效实例） |
| POST | `/ai/model/{id}/refresh` | `ai:model:config` | 刷新单个模型状态（健康探测） |
| POST | `/ai/model/refresh-all` | `ai:model:config` | 刷新所有模型状态 |
| DELETE | `/ai/model/{id}` | `ai:model:delete` | 删除模型配置 |

### 4.3 文档管理（AiDocumentController，5 个）

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| POST | `/ai/document/upload` | `ai:document:upload` | 上传文档（multipart，pdf/docx/txt） |
| GET | `/ai/document/list` | `ai:document:query` | 文档列表（分页） |
| GET | `/ai/document/{id}` | `ai:document:query` | 文档详情（含解析内容） |
| GET | `/ai/document/{id}/summary` | `ai:document:analyze` | 生成文档摘要 |
| DELETE | `/ai/document/{id}` | `ai:document:delete` | 删除文档 |

---

## 5. 配置项

`application.yml`（前缀 `ai`，绑定 `AiModuleConfig`）：

```yaml
ai:
  enabled: ${AI_ENABLED:true}                        # 模块开关
  client:
    connect-timeout: ${AI_CLIENT_CONNECT_TIMEOUT:5000}   # 上游连接超时（毫秒）
    read-timeout: ${AI_CLIENT_READ_TIMEOUT:60000}       # 上游读超时（毫秒），流式响应需较大缓冲（16MB）
  models:
    default-model: ${AI_DEFAULT_MODEL:deepseek-chat}     # 默认模型编码（按 modelName/modelCode 匹配）
    default-temperature: ${AI_DEFAULT_TEMPERATURE:0.7}   # 模型未配置温度时的默认值
    default-max-tokens: ${AI_DEFAULT_MAX_TOKENS:4096}    # 模型未配置 maxTokens 时的默认值
```

上游 `WebClient` 由 `AiClientConfig#llmWebClientBuilder` 构建（`@Qualifier("llmWebClientBuilder")`），超时参数即来自 `ai.client.*`。

---

## 6. 数据库表结构

| 表 | 说明 | 关键字段 |
|----|------|---------|
| `ai_model_config` | **唯一模型配置源** | `model_name`、`model_code`、`provider`、`api_endpoint`（base-url）、`api_key`、`max_tokens`、`temperature`、`context_window`、`input_price`/`output_price`、`is_default`、`status` |
| `ai_chat_conversation` | 对话会话 | `conversation_id`（业务ID）、`user_id`、`model_id`、`title`、`summary`、`total_messages`、`total_tokens`、`total_cost`、`last_message_time` |
| `ai_chat_message` | 对话消息 | `message_id`、`conversation_id`、`role`（user/assistant）、`content`、`input_tokens`/`output_tokens`、`cost`、`model_id`、`parent_message_id`、`metadata`、`status`、`error_msg` |
| `ai_document` | 文档 | `attachment_id`（关联附件）、`file_name`、`content`（解析文本）、`summary`、`model_name`、`status`、`error_message` |
| `ai_api_usage` | 用量统计 | `user_id`、`model_id`、`usage_date`、`request_count`、`success_count`/`failure_count`、`total_input_tokens`/`total_output_tokens`、`total_cost`、`avg_response_time` |

---

## 7. 模型配置指南

### 7.1 各 provider 端点示例（`api_endpoint` 存 base-url）

| provider | api_endpoint |
|----------|--------------|
| deepseek | `https://api.deepseek.com/v1` |
| qwen | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| glm | 智谱开放平台 OpenAI 兼容端点 |
| ernie | `https://qianfan.baidubce.com/v2` |

### 7.2 新增模型步骤

1. 前端「AI 模型管理」页新增，或直接 `POST /admin-api/ai/model`：填 `modelName`（展示名）、`modelCode`（上游模型编码，如 `deepseek-chat`）、`provider`、`apiEndpoint`、`apiKey`
2. 如需设为默认：`PUT /ai/model/{id}/default`
3. 用「刷新状态」（`POST /ai/model/{id}/refresh`）做健康探测（`max_tokens=1` 试调），确认可用

### 7.3 修改配置的生效方式

`ai_model_config` 变更后模型实例按**配置指纹**自动失效重建（见 2.4），也可调 `POST /ai/model/refresh` 主动刷新缓存，**无需重启应用**。

---

## 8. 安全注意事项

- **apiKey 明文存储**：当前 `ai_model_config.api_key` 未加 `@EncryptField`，且 `/ai/model/list`、`/ai/model/{id}` 直接返回实体（含明文 key）。权限码 `ai:model:query`/`ai:model:config` 仅应授予管理员；后续迭代建议对齐 screen 模块做法（列表接口排除敏感字段 + `@EncryptField` 加密存储）
- **流式接口无 OperationLog**：`/message/stream` 为 Flux 响应不加操作日志，用户消息落库由 `prepareStreamChat` 完成
- 权限码清单：`ai:chat:{create|query|edit|delete}`、`ai:model:{query|add|config|switch|delete}`、`ai:document:{upload|query|analyze|delete}`

---

## 9. 故障排查

| 现象 | 排查方向 |
|------|---------|
| 对话返回"无可用模型" | `ai_model_config` 无 `status=1` 记录；或默认模型编码（`ai.models.default-model`）配置错误 |
| 模型调用超时 | 上游网络/代理；调大 `ai.client.read-timeout`（流式长回答需要更长的读超时） |
| 流式响应中断且无 [DONE] | SSE 帧出现 `{"error":true,...}`；查看服务端日志上游报错（apiKey 无效、余额不足、模型编码错误） |
| 文档上传报"仅支持 pdf/docx/txt" | `TikaDocumentParser` 扩展名白名单限制（`SUPPORTED_EXTENSIONS`） |
| 摘要生成失败 | 检查文档 `content` 是否解析成功（`ai_document.status`/`error_message`），以及默认模型可用性 |
| 修改配置后行为未变 | 调 `POST /ai/model/refresh` 主动失效模型实例缓存 |

---

## 10. 测试运行说明

```bash
# 在 apps/forge-server 目录下
mvn test -pl forge-module-ai/forge-module-ai-biz    # 运行 AI 模块全部测试
```

| 测试类 | 覆盖 |
|--------|------|
| `OpenAiChatModelFactoryTest` | 模型实例构建与指纹缓存失效 |
| `SpringAiLlmClientTest` | 门面非流式/流式调用 |
| `AiModelResolverTest` | 三级回退解析逻辑 |
| `DocumentSummarizerTest` | 摘要 prompt 模板与风格选择 |
| `TikaDocumentParserTest` | 文档解析与格式白名单 |

---

*文档版本：v1.0*
*最后更新：2026-10-12*

# AI 模块（forge-module-ai）使用手册

> 适用范围：`apps/forge-server/forge-module-ai`（后端）与 `apps/forge-web/src/views/ai`（前端管理页）。
>
> AI 能力全部在 Java 端实现（基于 Spring AI 1.1.8 + LangGraph4j 1.9，无独立 AI 服务进程），四个 provider（deepseek/qwen/glm/ernie）统一走 OpenAI 兼容协议直连上游。

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
| Agent 编排 | LangGraph4j 编排图（代码定义 graph_key）：工具决策循环 + 高危工具人工审批中断 + 检查点恢复，对话与定时任务双入口触发 |
| Agent 团队协作 | Supervisor 调度式多 Agent：主管模型按成员职责动态分派子任务，各专家独立 prompt/模型/工具白名单执行并汇报，全程复用审批中断与检查点机制 |

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
├── agent/                              # Agent 编排（LangGraph4j）
│   ├── kernel/                         # 编排内核：AgentGraphDefinition/Registry/Runner/State/Context + MysqlCheckpointSaver
│   ├── graph/                          # 编排图：ConversationalAssistantGraph（单 Agent）/ SupervisorTeamGraph（团队协作）/ DecisionJsonParser
│   ├── tools/                          # 工具：ToolRegistry + SqlQueryTool(高危) / HttpFetchTool / TableSchemaInspector
│   ├── job/                            # 定时任务：AgentJobTask（Quartz 入口）/ AiAgentJobService / 数据清理
│   └── lifecycle/                      # 运行数据生命周期清理
└── controller/admin/                   # AiChatController / AiModelController / AiDocumentController / AiAgentController / AiAgentTeamController
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

### 2.6 Agent 编排架构（LangGraph4j）

**内核（agent/kernel/）：**

| 组件 | 职责 |
|------|------|
| `AgentGraphDefinition` | 图定义接口：graph_key 对应一个实现，`build(ctx)` 构建图结构 |
| `AgentGraphRegistry` | 注册表：graph_key → 图定义（Spring Bean 自动收集） |
| `AgentGraphRunner` | 执行器：`runStream`（单 Agent）/ `runTeamStream`（团队）/ `resume`（审批恢复），SSE 事件推送与 run/step 落库 |
| `MysqlCheckpointSaver` | 检查点持久化（`ai_agent_checkpoint` 表，恢复运行状态的依据） |

**内置图（agent/graph/）：**

- `ConversationalAssistantGraph`（graph_key=`conversational-assistant`，单 Agent）：

```
START → multimodal（图片理解，多模态模型，无图自动跳过）
          ▼
       decision（决策节点）◀────────────┐
          │ 严格 JSON 决策：            │
          ├─ action=tool ──→ approval（高危且未审批）──approved──→ tools ──┘
          │                    │__rejected→ summary      │
          └─ action=final ──→ summary（流式总结）→ END
```

  - **decision（工具决策循环核心）**：以该 Agent 的 `system_prompt` + 可用工具清单 + 数据库真实表结构 + `user_prompt_template({input})` + 图片识别结果 + 已执行工具历史构造提示词，决策模型输出严格 JSON：`{"action":"tool","calls":[{"tool":"sql_query","args":{...}}]}` 或 `{"action":"final","answer":"…"}`；解析失败自动重试一次，仍失败按自然语言兜底 final（空输出判失败）
  - **approval（高危审批中断）**：待执行工具含高危（`sql_query`）且本轮未经审批时抛 `GraphInterruptException` 中断——run 置 `WAITING_APPROVAL` 并生成人工待办（含待执行 SQL 明细）；「人工待办」页通过后从检查点恢复继续执行，驳回则跳过执行直接进入总结
  - **tools（工具并行执行）**：多个工具调用并行跑，结果（成功/失败）追加进工具历史供下一轮决策参考，回到 decision 循环
  - **summary（总结回答）**：决策为 final 时直出答案；达到 `max_steps` 上限时由总结模型基于全部工具记录流式总结（SSE 增量打字机输出）
  - **护栏**：决策循环 ≤ `max_steps`（Agent 配置，默认 8）+ recursionLimit = max_steps×3，防死循环
  - **可配置项**：`system_prompt`（人格/工具使用策略）、`user_prompt_template`、`model_bindings`（decision/multimodal/summary 三节点各自绑模型）、`max_steps`；图的节点与边结构代码写死

- `SupervisorTeamGraph`（graph_key=`team-supervisor`，多 Agent 团队协作）：

```
START → supervisor（主管模型决策：按成员职责分派/给最终答案）
          │ dispatch → expert_decide（专家决策：其 Agent 的提示词+模型+白名单工具）
          │               ├─ 高危工具 → expert_approval（中断，人工待办，approve/reject 恢复）
          │               └─ 工具执行 → 循环决策 → expert_report（汇报回填）
          │ final / 达 max_rounds → summary（流式总结）→ END
```

- 专家为"逻辑子图"：复用单 Agent 的审批中断（专用 `expertApproval` 状态键，驳回不污染后续专家）、检查点恢复、SSE 事件（payload 附 `agentCode/agentName`）
- 护栏三层：团队 `max_rounds`（主管轮次）→ 成员 `max_steps`（专家内循环）→ recursionLimit 按成员步数动态估算

**工具（agent/tools/）：** `sql_query`（高危，走 SQL 安全层+租户改写）、`http_fetch`（白名单抓取）；团队成员可配 `allowed_tools` 白名单（空=全部）

**触发入口：** 对话 `POST /ai/agent/chat/stream`（`agentCode` 与 `teamCode` 二选一）；定时任务 `ai_agent_job`（`agent_code` 与 `team_code` 二选一）

---

## 3. 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| AI 对话 | `views/ai/chat` | 对话窗口：发送消息（流式渲染）、模型切换、消息历史；Agent 模式下单 Agent/团队下拉选择、执行时间线 |
| 会话管理 | `views/ai/conversation` | 对话列表：分页查询、改名、删除 |
| 文档管理 | `views/ai/document` | 文档上传（pdf/docx/txt）、内容查看、摘要生成 |
| 模型管理 | `views/ai/model` | 模型配置：新增/编辑、启停、默认模型切换、状态探测刷新 |
| Agent 管理 | `views/ai/agent/config` | Agent 配置：提示词/模型绑定/步数上限编辑、启停 |
| Agent 团队 | `views/ai/agent-team` | 团队配置：Supervisor 提示词/模型绑定/轮次上限 + 成员子表（选 Agent/职责/工具白名单/步数上限） |
| 运行历史 | `views/ai/agent/run` | Agent/团队运行记录分页、步骤明细抽屉（来源列区分单 Agent 与团队） |
| 人工待办 | `views/ai/agent/todo` | 高危工具审批：通过/驳回（团队与单 Agent 通用） |
| Agent 任务 | `views/ai/agent/job` | 定时任务定义（agentCode/teamCode 二选一）、手动执行、调度管理 |

对应 API 定义：`src/api/ai/chat.ts`、`src/api/ai/model.ts`、`src/api/ai/document.ts`、`src/api/ai/agent.ts`、`src/api/ai/agentTeam.ts`、`src/api/ai/sql-whitelist.ts`。

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

### 4.4 Agent 编排（AiAgentController / AiAgentJobController）

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| POST | `/ai/agent/chat/stream` | `ai:agent:run` | Agent/团队对话（SSE，`agentCode` 与 `teamCode` 二选一） |
| GET | `/ai/agent/config/list` | `ai:agent:query` | Agent 配置列表（可按 status 过滤） |
| PUT | `/ai/agent/config/{id}` | `ai:agent:config` | 更新 Agent 配置 |
| GET | `/ai/agent/run/list` | `ai:agent:query` | 运行记录分页（团队运行带 teamName） |
| GET | `/ai/agent/run/{id}` | `ai:agent:query` | 运行详情（含最终回答） |
| GET | `/ai/agent/run/{id}/steps` | `ai:agent:query` | 运行步骤明细（团队步骤含专家标识） |
| GET | `/ai/agent/conversations` | `ai:agent:query` | Agent 会话列表（按会话分组） |
| DELETE | `/ai/agent/conversation/{id}` | `ai:chat:delete` | 删除 Agent 会话 |
| GET | `/ai/agent/todo/list` | `ai:agent:query` | 人工待办分页 |
| POST | `/ai/agent/todo/{id}/approve` | `ai:agent:approve` | 待办通过（恢复执行） |
| POST | `/ai/agent/todo/{id}/reject` | `ai:agent:reject` | 待办驳回 |
| POST | `/ai/agent/data-lifecycle/clean` | `ai:agent:data-clean` | 手动清理运行数据 |
| GET/POST/PUT/DELETE | `/ai/agent/job/**` | `ai:agent:job:*` | Agent/团队定时任务 CRUD 与手动执行 |

### 4.5 Agent 团队（AiAgentTeamController，5 个）

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/ai/team/list` | `ai:team:query` | 团队列表（含成员聚合，agentCode/agentName 回显） |
| GET | `/ai/team/{id}` | `ai:team:query` | 团队详情 |
| POST | `/ai/team` | `ai:team:add` | 新增团队（含成员整体保存） |
| PUT | `/ai/team/{id}` | `ai:team:edit` | 更新团队（成员全删全插重建） |
| DELETE | `/ai/team/{ids}` | `ai:team:remove` | 删除团队（级联删成员） |

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
| `ai_agent_config` | Agent 配置 | `agent_code`、`graph_key`（编排图）、`system_prompt`、`user_prompt_template`、`model_bindings`（节点→模型）、`max_steps`、`status` |
| `ai_agent_team` | Agent 团队（多 Agent 协作） | `team_code`、`supervisor_prompt`（主管调度规则）、`model_bindings`（supervisor/summary）、`max_rounds`、`status` |
| `ai_agent_team_member` | 团队成员 | `team_id`、`agent_id`（引用 ai_agent_config）、`member_role`（职责，注入主管提示词）、`allowed_tools`（白名单，空=全部）、`max_steps`、`sort_order` |
| `ai_agent_run` | 运行记录 | `agent_id`（团队运行为 NULL）、`team_id`、`conversation_id`、`trigger_type`、`status`（RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED）、`final_answer` |
| `ai_agent_run_step` | 运行步骤明细 | `run_id`、`node_name`、`step_type`（LLM/TOOL/MULTIMODAL/INTERRUPT/SUPERVISOR）、`tool_name`、`step_output`（含专家标识）、`tokens_in/out`、`duration_ms` |
| `ai_agent_todo` | 人工审批待办 | `run_id`、`content`（待执行高危工具明细）、`status`（PENDING/APPROVED/REJECTED）、`handler_*` |
| `ai_agent_checkpoint` | 检查点（LangGraph4j） | `run_id`、`thread_id`、`checkpoint_id`、`state`（状态快照 JSON）——审批恢复依据 |
| `ai_agent_job` | Agent 定时任务 | `agent_code`/`team_code`（二选一）、`input_template`（{date} 等占位符）、`cron_expression`、`sys_job_id` |

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
| 运行状态 WAITING_APPROVAL | 专家/Agent 要执行高危工具（如 `sql_query`）被中断，去「人工待办」审批；通过后自动恢复执行 |
| 团队运行 FAILED，错误含"输出被 max_tokens 截断" | 思考类模型（如 GLM-4.7-FlashX）推理耗尽 token 预算，多轮后 prompt 变长更易触发；Supervisor/决策模型建议绑定非思考模型或调大 `max_tokens`，也可配置同类型备用模型走故障转移 |
| 团队运行 FAILED，提示"决策模型未绑定" | 成员引用的 Agent 未配置 `model_bindings.decision`；在 Agent 管理页为该 Agent 绑定 text 模型 |
| 团队对话不保存历史 | 发送时未归属会话（历史版本缺陷已修复）；确认前端为懒创建会话后的版本 |
| 审批后前端无响应 | 审批恢复依赖 WS 推送 + 轮询兜底；查浏览器 WS 连接与后端 `/ws` 端点 |

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

*文档版本：v1.2*
*最后更新：2026-10-15*

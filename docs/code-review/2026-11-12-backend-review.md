# forge-admin 后端 Java 服务全面代码评审报告

- **日期**：2026-11-12
- **范围**：apps/forge-server 全部后端（819 个 Java 文件 / 约 7.44 万行）：forge-module-ai、forge-module-workflow 深评（逐文件），forge-module-system、forge-framework（6 starter）、forge-module-screen、forge-module-demo、forge-server 标准扫（异味定位 + 热点深读）
- **方式**：8 个并行评审代理分模块执行，统一 10 维度（架构/正确性/并发/事务/异常/安全/代码质量/规范/性能/测试盲区）；FlowLong 引擎行为经 `flowlong-core-1.2.7.jar` 字节码（javap）核实；最终汇总时对 6 项最重结论做了人工亲核（见 §8）
- **依据**：`docs-base/development/backend-development-specification.md` v1.0、ArchUnit `ArchitectureTest` 五组规则
- **定级**：P0=安全漏洞/数据损坏/线上必现；P1=明确缺陷或高风险；P2=可维护性；P3=风格规范。每条标注置信度（实证=代码路径已闭环；疑似=代码面成立、运行时表现待验证）
- **纪律**：纯评审，未修改任何代码；修复建议仅供后续任务参考

---

## 1. 总览

| 模块 | 文件/行数 | P0 | P1 | P2 | P3 | 小计 |
|---|---|---|---|---|---|---|
| AI 模块（client 层） | 161 文件 / 16,606 行 | 0 | 3 | 6 | 4 | 13 |
| AI 模块（agent 编排） | ↑ | 0 | 5 | 7 | 6 | 18 |
| AI 模块（service+tools 安全审计） | ↑ | 0 | 7 | 11 | 5 | 23 |
| Workflow（两大 ServiceImpl） | 213 文件 / 25,332 行 | **1** | 6 | 12 | 5 | 24 |
| Workflow（framework 扩展点+调度） | ↑ | 0 | 7 | 9 | 6 | 22 |
| Workflow（im+callback+demo） | ↑ | 0 | 10 | 11 | 4 | 25 |
| system（标准扫） | 295 文件 / 20,976 行 | 0 | 3 | 7 | 6 | 16 |
| framework+screen+server（标准扫） | 139 文件 / 10,500 行 | 0 | 9 | 15 | 10 | 34 |
| **合计** | 819 文件 / 74,448 行 | **1** | **50** | **78** | **46** | **175** |

**一句话结论**：功能正确性与测试护栏的底子是好的（亮点见 §6），但**横向越权（资源归属校验）大面积缺失**、**多租户 `TENANT_ENABLE=true` 是大面积雷区**、**异步链路（线程池/租户上下文/外部调用超时）系统性欠账**三大主题贯穿全后端；两大 workflow ServiceImpl（合计 3,139 行）与 AI 两个 Graph 的重复漂移是最大的可维护性债务。

**定级说明**：以下 4 组问题代理各自判 P1，但按"确定性击穿安全控制 + 无需特殊条件触发"衡量，汇总时建议**按 P0 紧急度处置**（§2 前 4 条）。

---

## 2. 立即修复清单（Top 10，按紧急度排序）

> **修复状态（2026-11-12）**：§7 第一批「安全堵口」已由 FlowNeo 任务 `security-hardening-batch1` 完成并全量回归通过（唯一失败 SqlSafetyEndToEndIT 为本机无 Docker 的环境限制，非回归）。本清单覆盖情况：**1✅ 2✅ 3✅ 4✅ 5✅ 6✅ 7✅ 8✅ 已修复**；**9（流式资源泄漏/竞速污染）、10（AI 决策 45s 超时）属第二批稳定性，未在本批**。附加项：触发器 SpEL 收敛 ✅、CORS 白名单接线 ✅、JWT/AES 默认密钥移除（prod fail-fast）✅。

1. **任务动作归属校验缺失（P0，实证）** — `WfTaskServiceImpl.java:681/784/951/873/969/1000`。reject/return/withdraw/减签/加签/抄送六类动作只做任务存在性校验；引擎侧（flowlong 1.2.7 字节码核实）`undoHisTask/removeTaskActor/createCcTask/addTaskActor/executeJumpTask(rejectJump)` 均无 `isAllowed` 参与者校验（`allowedCheck` 仅 active/jump/complete）。任何持 `workflow:task:complete` 的用户可驳回/撤回/删除参与者**他人**的任务，撤回直接删除他人已办记录。→ 抽 `validateTaskOwnership`（查 flw_task_actor/flw_his_task_actor，管理员豁免），六入口统一调用。
2. **默认 SecurityFilterChain fail-open + 生产 actuator 暴露（代理 P1，建议按 P0 处置，实证）** — `SecurityConfig.java:181` 默认链 `anyRequest().permitAll()`：`/admin-api|/app-api|uploads` 之外一切路由全放行；叠加 `application-prod.yml:44` 的 `management:` 块误缩进在 `spring:` 下永不生效 → **生产 `/actuator/prometheus` 实际公开暴露**。→ 默认链改 `authenticated()`；`management` 提为顶级键。
3. **AI 会话/文档/运行记录横向越权（代理 P1，建议按 P0 处置，实证）** — `AiChatServiceImpl.java:64-117` 等：`pageConversationList` 无 userId 条件、`getConversation/getMessages` 按 id 直查（任意用户枚举读**全部用户** AI 对话正文）；`updateTitle/deleteConversation/save-ai` 可改/删他人会话、**伪造 assistant 消息**；`AiDocumentServiceImpl` 详情/删除/摘要、`AiAgentRunServiceImpl` 运行/步骤（含工具入参出参）同病。同文件 `generateTitle/pageDocument/listConversations` 已有正确写法——是漏写不是没规范。→ 抽 `requireOwnedConversation/requireOwnedDocument`，全部入口收口。
4. **Agent 审批门"一次批准、全程放行"（代理 P1，建议按 P0 处置，实证）** — `ConversationalAssistantGraph.java:334-343`、`SupervisorTeamGraph.java:595-604`：`APPROVAL` 状态值只要 present 即视为已批准（**REJECTED 也会放行**），且单 Agent 图全图无清除点——用户批准一次后，同 run 内模型后续发起的任意新高危调用（UPDATE/DELETE SQL）全部绕过人工审批直达执行。→ tools 节点完成后重置 APPROVAL。
5. **微信登录 Mock 认证后门（P1，实证）** — `AppAuthServiceImpl.java:189-196`：appId 空白即 Mock 模式且无 profile 守卫，生产漏配 `forge.wx.mini-app-id` 时任何人不需微信凭证即可建号拿合法 JWT。→ Mock 加环境开关+生产 fail-fast。
6. **大屏 SQL 三道闸被击穿三处（P1×3，实证）** — `SqlDataSourceExecutor.java:71-92` 执行层用**原始模板**而非 deparsed SQL（`/*! ... */` MySQL 执行注释可绕过表/列白名单，SqlSafetyGuard 自己的 Javadoc 契约被违背）；`SqlSafetyGuard.java:92-121` JOIN 表与 WHERE/ORDER BY 列完全不做白名单校验（`ORDER BY SLEEP(1)` 也不在危险函数扫描内）；`SqlDataSourceExecutor.java:116-139` LIMIT 占位符改名（`:n`）即无界拉取可 OOM。→ 执行改 deparsed SQL / 全树表列提取 / LIMIT 参数名 AST 级绑定 + `setMaxRows` 兜底。
7. **回调 secret 明文回传 + 空 secret 静默不签名（P1，实证）** — `WfBusinessCallbackServiceImpl.java:162`：secret 原样进 Response 返回前端（持 `workflow:callback:list` 即可读出 HMAC 密钥伪造回调签名）；明文落库（IM 侧已用 AES-256-GCM，双标）；update 漏传 secret 即置 null → 投递静默降级为无签名。→ 照抄 `ImChannelConfigResponse.hasSecret` 模式 + EncryptTypeHandler + 空=保留原值。
8. **demo 范本状态伪造（P1，实证）** — `ProjectWorkflowEventHandler.java:40-80` + 通用发起端点 `businessKey` 无校验：持 `workflow:instance:start` 的用户可用伪造 `businessKey=project:{id}` 发起可控流程并走到终态，把他人/已驳回的立项翻转为 APPROVED 或 CANCELED。范本会被复制，坏示范扩散最快。→ 回写校验 instanceId 一致 + `eq(approveStatus, APPROVING)` CAS。
9. **流式资源泄漏 + 竞速输家污染 SSE（P1×2，实证）** — `OpenAiCompatStreamClient.java:144-157/473-482` 下游断连后同步读循环无取消感知，socket+boundedElastic 线程最坏挂 10 分钟（批量断连耗尽线程池）；`SpringAiLlmClient.java:416-440` 决策竞速输家不取消且共享 onDelta 回调，胜者已推进后输家持续向前端推交错事件。→ `emitter.onCancel` 置位 + 读循环检查；竞速加"胜者已定"门控。
10. **AI 决策 45s 超时根因（P1，实证——即线上未定案问题的代码面答案）** — `AiApprovalExecutor.java:111`、`WfAiRoutingServiceImpl.java:135`：`supplyAsync` 走 **commonPool** + `future.cancel(true)` 对 CompletableFuture 不中断底层线程 + 底层用**无超时 `chat()`** 而非自带 decision-timeout 的 `chatForDecision`——每笔 30s 超时都在 commonPool 留一个烧到 45s+ 的线程。→ 改用 `chatForDecision` + 显式 executor。

---

## 3. 多租户开闸前置清单（TENANT_ENABLE=true 前必须修复）

当前默认 `tenant.enable=false` 掩盖了以下全部问题，**开启租户即炸**：

| # | 位置 | 后果 | 置信度 |
|---|---|---|---|
| 1 | `AiAgentController.java:214`、`SupervisorTeamGraph.java:447-463`（租户快照两处漏网） | Agent SSE 对话整体不可用（首个 insert NPE），专家子图全部降级 | 实证 |
| 2 | `WorkflowTimeoutJob/WorkflowReminderJob`（Quartz 线程无租户上下文） | 超时/提醒两个 Job 每轮 NPE 全瘫 | 实证 |
| 3 | `ImChannelConfigService.resolveTenantId` + workflowExecutor 未包 TTL（池线程滞留创建时刻租户） | IM 推送串租户/跨企业错发或 NPE 重试风暴 | 实证（推演） |
| 4 | `BusinessCallbackEventListener.enabledCallbacks` 依赖拦截器而非显式租户 | 跨租户把他租户 payload POST 到他租户 URL（数据外泄） | 实证（条件性） |
| 5 | `wf_event_audit` 无 tenant_id 且不在 ignore-tables | 租户模式下事件审计与终败兜底整体失效 | 实证 |
| 6 | screen 四表、`ai_document`/`ai_chat_*` 无 tenant_id 且不在 ignore-tables | 对应模块整体 SQL 报错 | 实证 |
| 7 | `TenantAutoConfiguration` 租户插件注册在分页插件之后（违反 MP 要求） | UNION 分页 COUNT 可能漏租户条件 | 疑似（需开租户实测） |
| 8 | `TenantRedisCacheManager` 租户前缀在缓存实例首次创建时烘焙 | 未列入 ignore-caches 的缓存"首租户独占"静默串台 | 实证（当前无泄露数据缓存名） |

根治方向：租户 ID 在**事件发布时刻**快照进事件对象、全链显式传参；`agentExecutor/workflowExecutor` 用 `TtlExecutors` 包装。

---

## 4. 分模块问题清单

> 每条格式：**[级别] 标题**（位置）— 问题与影响 → 修复方向。（置信度省略处均为实证；疑似已单独标注）

### 4.1 AI 模块 — client 层（13 条）

**P1（3）**
- **streamHeadersTimeout 被 clamp 回 30s**（OpenAiCompatStreamClient.java:222）— `Math.min(30_000, headersTimeout)` 静默压掉 120s 配置；e0593a2 修的"排队超时误杀"被 92862b5 手写 socket 重写悄悄回退（提交历史可追溯）。→ clamp 独立成 first-byte 探测超时，headersTimeout 全额生效。
- **断连后同步读循环无取消感知**（OpenAiCompatStreamClient.java:144-157,269-270,473-482）— socket+boundedElastic 线程最坏挂 10 分钟，批量断连耗尽线程池（默认 10×CPU）。→ `emitter.onCancel` 置 volatile 标志，读循环周期检查。
- **决策竞速输家污染 SSE**（SpringAiLlmClient.java:416-440,260-261,322-326）— 输家不取消、共享 onDelta/onReasoning 回调，胜者采用后输家继续往前端推交错 THOUGHT/REASONING；输家流占线程至 45s 超时并可能误写健康负缓存。→ "胜者已定"AtomicBoolean 门控 + 输家静默。

**P2（6）**
- **unsupportedCache 无 TTL**（NativeToolCalling.java:58-73,167-175）— 字符串启发式（message.contains("400")+tool）误判即永久禁用 native 路径。→ `expireAfterWrite` 10~30min。
- **7 常量硬编码 + connect-timeout 死配置**（OpenAiCompatStreamClient.java:71-105,222 + application.yml:295）— `ai.client.connect-timeout` 全库无消费方；read-timeout yml 与代码双默认漂移。→ 统一挂 `ai.client.*`。
- **临时诊断日志滞留 INFO**（OpenAiChatModelFactory.java:56-57,133-135 + NativeToolCalling.java:108-110）— e1fcafb 自述"临时诊断"，高流量线性刷屏。→ 降 debug/删除。
- **DechunkingInputStream 边界**（OpenAiCompatStreamClient.java:429-447）— 畸形 chunk 头抛 NumberFormatException 绕过节点重试语义；chunk 中途 EOF 被静默当正常结束（用户拿到截断内容无标记）。→ 包成 IOException；DATA 态 EOF 改报"流截断"。
- **5s 停顿检测把 [DONE] 迟到计入**（OpenAiCompatStreamClient.java:183-196,491-497）— 好节点可能被误冷却 5min；思考模型长停顿无余量（疑似）。→ [DONE] 不计 gap；阈值外化并留余量。
- **URL 规范化两份手工副本**（OpenAiChatModelFactory.java:171-180 ↔ SpringAiLlmClient.java:615-625）→ 抽公共工具 + 单点测试。

**P3（4）**
- 正文全限定名残留（SpringAiLlmClient/NativeToolCalling/OpenAiChatModelFactory 多处，穷举见代理报告）→ 补 import。
- AgentToolCallback 手工拼 JSON Schema/错误串，转义不全（:35-67）→ 用已持有的 ObjectMapper。
- AI 模块定义通用 RestTemplate 供 system 使用且无超时（AiClientConfig.java:22-25）→ 移 framework + 配超时。
- failover("exhausted") 把"未尝试任何候选"也计数（SpringAiLlmClient.java:391-409）→ 口径拆分。

### 4.2 AI 模块 — agent 编排（18 条）

**P1（5）**
- **审批残留绕过高危门**（ConversationalAssistantGraph.java:334-343、SupervisorTeamGraph.java:595-604）— 见 §2-4。
- **agentExecutor 单池四角色 + queue=200 使 max=16 失效 + join 自等待可死锁**（AgentExecutorConfig.java:24-36、SupervisorTeamGraph.java:465,999）— ≥8 并发 run 进工具阶段即全部死锁；CallerRunsPolicy 救不了。→ 工具并行独立池 / join 改 orTimeout。
- **租户快照两处漏网**（AiAgentController.java:214、SupervisorTeamGraph.java:447-463）— 见 §3-1。
- **DecisionJsonParser.convertValue 未捕获异常**（DecisionJsonParser.java:46,53）— `"args"` 为字符串/数组（弱模型常见）时 IllegalArgumentException 冒泡，破坏"解析失败返回 null"契约，重试/降级全失效（疑似-Jackson3 异常类型未实测）。→ try-catch 或 `isObject()` 前置校验 + 单测。
- **handleTodo→resume 并发双跑**（AiAgentRunServiceImpl.java:132-176）— PENDING 快照读 + updateById 无条件更新，双审批人并发触发同一 run 双执行、检查点交错 upsert。→ `UPDATE ... WHERE status='PENDING'` 抢占。

**P2（7）**
- **两 Graph 四组重复且已漂移**（ConversationalAssistantGraph ↔ SupervisorTeamGraph）— extractContentDelta/summaryTimeoutMs/nullToZero 完全一致；describePendingCalls 已行为漂移；toolsNode/expertToolsNode ~60 行骨架、summary/decide 重试骨架重复。→ 抽 `GraphSupport`/`ToolParallelExecutor`。
- **专家子图 InterruptedException 无痕吞掉**（SupervisorTeamGraph.java:454-456）— 专家静默消失，无日志无事件。→ log.error + SKIPPED 事件。
- **三处 join() 无超时 + 僵尸 run 无回收**（SupervisorTeamGraph.java:465,999、ConversationalAssistantGraph.java:413 + AgentDataCleanupService 只删已完结）→ orTimeout + RUNNING 超 N 小时判 FAILED。
- **checkpoint 每写前全量 SELECT+逐行反序列化**（MysqlCheckpointSaver.java:59-76）— writeInterval=1 时 O(N²)；无 LIMIT。→ LIMIT 1 或惰性反序列化。
- **正文全限定名大量残留且两次专项清理后复发**（SupervisorTeamGraph/ConversationalAssistantGraph/AgentGraphRunner 穷举）→ 一次性清理 + **ArchUnit 加守卫规则防第三次复发**。
- **DecisionParseTest 是实现的影子拷贝**（不 import 被测类，自带复制品）→ 改为直接测 DecisionJsonParser。
- **状态 list mutate-in-place 依赖框架浅覆盖语义**（toolsNode 等 4 处，疑似）→ 返回新副本 + AgentGraphState 注释声明约定。

**P3（6）**
- PromptBudgeter 注释宣称"相关性降序"实为字典序截断（:88-122）。
- StepRecorder 把 INTERRUPTED 步骤落库为 SUCCESS；insertWithRetry 对确定性失败盲目同参重试（:131,102-107）。
- retryUsedNative() 恒 false 死代码 + decide 的 retry 参数迷惑（ConversationalAssistantGraph.java:193,218,278-281）。
- DecisionJsonParser 多块合并会把示例 JSON 误并入 calls（:37-62，疑似触发）。
- truncate 负数 limit 抛越界（PromptBudgeter.java:232-237）→ Math.max(0,limit)。
- updateJob 无法 agent↔team 模式切换（AiAgentJobServiceImpl.java:62-79）→ ""=清除语义。

### 4.3 AI 模块 — service+controller+tools（23 条）

**P1（7）**
- **会话横向越权（读）**（AiChatServiceImpl.java:63-107,419-425）— 见 §2-3。
- **会话横向越权（写）**（AiChatServiceImpl.java:110-117,219-232,255-258,340-366,398-417 + AiChatController.java:160-167）— updateTitle/deleteConversation/sendMessage/prepareStreamChat（读他人 20 条历史当上下文）/save-ai（伪造 assistant 消息）均无归属校验。→ `requireOwnedConversation` 收口；save-ai 加幂等键。
- **文档横向越权**（AiDocumentServiceImpl.java:140-220）— 列表有过滤、详情/删除/摘要无校验。→ `requireOwnedDocument`。
- **Agent 运行记录无用户过滤**（AiAgentRunServiceImpl.java:40-126）— 运行/步骤（含 SQL 结果）/待办可被枚举。→ 非管理员加 userId。
- **HttpFetchTool 重定向绕过白名单（SSRF）**（HttpFetchTool.java:63-66,127-135）— 初始 URL 校验后 HttpURLConnection 自动跟随同协议 30x 不复检 → 白名单域名 302 跳 169.254.169.254 即成。→ 禁自动重定向+逐跳复检+拒绝 IP 字面量。
- **schema.table 绕过 SQL 白名单**（SqlTenantRewriter.java:104-109,222-235）— `other_db.ai_document` 取末段匹配即放行且保留前缀渲染，DB 账号有跨库权限即跨库读。→ 限定名直接拒绝（fail-closed）。
- **@Transactional 包住 LLM 外部调用**（AiChatServiceImpl.java:234-309）— Hikari 默认 10 连接，~10 并发对话耗尽连接池拖垮全站。→ 拆两段短事务/TransactionTemplate。

**P2（11）**
- "建会话+插消息"四处重复（AiChatServiceImpl.java:49-61,242-267,340-366,429-458）→ 抽 `resolveOrCreateConversation/insertMessage`。
- generateTitle 占位判定-更新竞态：双触发双计费、可覆盖用户手动标题（:141-175）→ 乐观锁 `WHERE title=旧值`。
- conversationId 不存在时静默生成孤儿消息（:256-257,444-445）→ 直接抛错。
- 流式消息依赖前端 save-ai 回传，断网丢消息/重试重复插入无幂等；记账失败静默（AiUsageRecorder.java:23-33）。
- 手工拼 JSON 错误帧转义不全 + 泄露内部 error.getMessage()（AiChatController.java:137-139）→ ObjectMapper+固定文案。
- 分页契约违例：pageRuns/pageTodos/pageJobs 返回自拼 Map、AiApiUsageController 裸 `Page<AiApiUsage>`（AiAgentController.java:88,132 等）→ PageResult+Response DTO。
- startDate/endDate 未校验格式，非法输入 500（AiUsageQueryServiceImpl.java:77-79）。
- SqlQueryTool 无 setQueryTimeout：白名单内自交 JOIN 可长时间占死连接（SqlQueryTool.java:69-76）→ setQueryTimeout(10s)+SQL 长度上限。
- AgentToolCallback 无上下文调用默认 UNRESTRICTED：internalToolExecutionEnabled 一旦打开即绕过 DocumentSearchTool 本人过滤（:56-66，当前默认关闭未触发）。
- 待办审批 check-then-act 可双恢复（AiAgentRunServiceImpl.java:130-148）→ 条件更新（与 4.2-#5 同型）。
- ai_document 等旧表无 tenant_id 且不在 ignore-tables：开租户必现功能损坏（表内注释已自认，属 ai-agent-opt-round2 遗留）。
- LIMIT 注入 `body.contains("limit")` 可被字符串字面量绕过（SqlQueryTool.java:143-147，setMaxRows 兜底，纵深失效）→ AST 判定。
- ChatRequest.content 无长度上限（ChatRequest.java:23-27）→ @Size。
- AiModelController 等直接以 Entity 作请求/响应，ApiKey 靠每出口手工 maskApiKey（机制性遗漏风险；当前 9 出口已全覆盖 ✓）。

**P3（上述并条后计 5）**：AiAgentController/AiAgentRunServiceImpl 正文 FQN+Controller 内嵌 DTO；标题提示词"20 字"与兜底 30 字双口径硬编码；详见代理报告。

**权限审计正面结论**：8 个 Controller 共 43 端点 `@PreAuthorize` **全覆盖无遗漏**；SSE 端点鉴权在 REQUEST dispatch 完成，ASYNC/ERROR permitAll 为框架必需、无未授权面。

### 4.4 Workflow — 两大 ServiceImpl（24 条）

**P0（1）**
- **六类任务动作归属校验缺失**（WfTaskServiceImpl.java:681/784/951/873/969/1000）— 见 §2-1。

**P1（6）**
- **催办节流 check-then-act 竞态**（WfTaskServiceImpl.java:1089-1097,1175-1188）— 并发催办双穿透 + variable JSON 读改写丢更新。→ 条件更新。
- **催办事务内同步发 WebSocket 通知**（:1051,1148-1154）— 回滚后通知已发出、慢发送拉长事务；模块内其余四处监听全用 AFTER_COMMIT，唯独催办直发。→ 改事件+AFTER_COMMIT。
- **approve/reject 等七个核心审批接口缺 @OperationLog**（WfTaskController.java:90-172）— 最需审计的动作反而没日志（claim/transfer/return 都有）。→ 补齐。
- **Instance 侧列表 N+1**（WfProcessInstanceServiceImpl.java:1331-1423,1204-1267）— 每行 4+ 次查询，pageSize=20 单请求 60~120+ 条 SQL；Task 侧 BatchContext 已批量化未同步。→ 仿建 InstanceBatchContext。
- **done/cc 列表无界捞全量历史参与者再拼巨型 IN**（WfTaskServiceImpl.java:488-508,1251-1272）→ 手写 JOIN/EXISTS 下推 DB。
- **租户插件注册顺序晚于分页插件**（TenantAutoConfiguration.java:55-61 + MybatisPlusConfig.java:57-61，疑似）— UNION 分页 COUNT 可能漏租户条件。→ 顺序前移+开租户断言。

**P2（12）**
- **Instance 上帝类 10 类职责**（1592 行）→ 拆 `WfProcessModelValidator`（模型校验独立单测）/`WfInstanceQueryService`/`ProcessInstanceAssembler`/`WfApprovalCommentService`/`WfWorkflowPermissionSupport`，主类剩编排 <500 行。
- **Task 上帝类 12 类职责**（1547 行）→ 拆 `WfTaskQueryService`/`TaskBatchLoader`+`TaskResponseAssembler`/`WfTaskActionService`/`WfUrgeService` + 与 Instance 共用支撑类。
- **checkNodeModelWithDynamicAssignee ~218 行**（Instance:247-465）— approval/cc/conditionNode 三段 setType 校验逐字复制（262-275/278-291/312-319）；循环体 14 类校验 8 层缩进。→ 抽 `checkAssigneeConfigured` + 按 TaskType 策略链。
- **startProcess ~112 行 8 职责串联**（Instance:129-243）→ 拆 buildDynamicAssignees/resolveInstanceTitle/saveStartComment。
- **todo/claimable/done 三方法同骨架 80-90 行 ×3**（Task:81-237,477-566）— 且 claimable 静默丢弃 title/processDefinitionName 过滤（前端搜索无效，行为缺陷）。→ 模板方法一法三态 + 补过滤。
- **四组跨类重复**：hasWorkflowAdminRole（Instance:633/Task:1190，admin/workflow_admin 角色码两份维护）、createFlowCreator（三处）、escapeLike（两处）、saveApprovalComment（两套+2 重载）；另有 emptyPage 双胞胎+3 处内联空页。→ WfWorkflowCommonSupport 收敛。
- **死代码**：convertTaskToResponseWithProcess(FlwTask) 单参版（Task:1437，N+1 反模式样板）、compareStartTimeDesc（Instance:1269）→ 删除。
- **"设置分类名称" TODO 未实现**（Instance:1373）— 实例列表/我的流程/详情的 categoryName 恒 null；Definition 侧已有 `getCategoryNameMap` 可复用。
- **getReturnNodes 比较器违反 Comparator 契约**（Task:856-866）— 两者皆 major 时对 (a,b)/(b,a) 均返回 1，TimSort ≥32 元素可能抛契约违例；且无任何时间比较，与注释不符。→ 拼接式排序。
- **审批意见附件绑定不校验属主**（Task:1388-1395）— 可枚举他人附件 ID 绑到自己评论，随后被所有可见者读取。→ 绑定前校验 create_by。
- **__title 模糊检索锚定 variable 大 JSON 列全表 LIKE**（WfInstanceQueryMapper.xml:25,43）— 转义正确无注入，但不可扩展。→ 标题冗余专用列+索引。
- **深嵌套三处点名**：Instance:338-358（8 层）、Instance:187-238（7 层 lambda 套 ifPresent）、Task:884-918（加签 switch 7 层）→ 随拆分降层。

**P3（5）**
- processDefinitionId 非数字三种处理并存（1049/1107 裸抛 500 vs 1174 静默吞）→ 统一 parseProcessId。
- 9 个写方法不判 currentUserId 为 null，"未知用户"静默落库 → requireCurrentUserId()。
- 流程名/任务名 LIKE 不转义 %_（title 却转义，双标准；参数绑定无注入）→ 统一 escapeLike。
- getProcessVariables 原样返回引擎内部变量（__title/initiator/lastUrgeTime 等出接口）→ 剔除 `__` 前缀键。
- `/definition/deploy` 落 ext 不写 useScope/useScopeUsers/titleRule 与模型部署路径双轨漂移（发起范围校验形同虚设，"是否仍被前端使用"未考证——疑似）；traverseNodes 五段同构遍历。

### 4.5 Workflow — framework 扩展点+调度（22 条）

**P1（7）**
- **PermissionTaskCreateInterceptor 已挂载但空转**（PermissionTaskCreateInterceptor.java:75）— 引擎字节码证实拦截器对每次任务创建生效，但 4 类声明式权限校验全是 TODO 只打 debug；节点级"需权限 X"被静默忽略（操作侧另有 AccessStrategy 把关，故 P1 非 P0）。→ 实现校验或删除配置项，勿留"配置了但不生效"的假安全。
- **AI 决策超时三重叠加**（AiApprovalExecutor.java:111、WfAiRoutingServiceImpl.java:135）— 见 §2-10。
- **RedisDistributedLock 误删他人锁**（RedisDistributedLock.java:88-98）— unlock 只校验共享前缀不校验持有者令牌；Job 跑超 30s 锁过期后 finally 删掉的是别的实例的锁 → 重复自动办理。→ Lua `GET==token then DEL`。
- **两个 Quartz Job 多租户每轮 NPE**（WorkflowTimeoutJob.java:58-83、WorkflowReminderJob.java:61-86）— 见 §3-2。
- **4 个候选人策略依赖 Web ThreadLocal（UserContext）**（DeptLeader/StartUserDeptLeader/Expression 等 :52-70,195-339）— 异步链路（AI 审批推进/超时自动办理/AFTER_COMMIT 抄送）NPE 上抛打断流转或静默空集错配。→ tenant 从 TaskContext 传入。
- **TaskContext.getStartUserId() 返回操作者非发起人**（ForgeTaskActorProvider.java:212-222）— 第二级起"发起人自己(36)/发起人部门负责人(37)/${initiator}"节点全部把上一审批人当发起人。→ 改 `execution.getFlwInstance().getCreateId()`。
- **审批人自选策略双重失效**（ApproveUserSelectStrategy.java:44、ForgeTaskActorProvider.java:192-194）— 变量全仓无写入方 + `getTaskKey()` 恒 null 拼出 `"NEXT_null_..."` → 功能名存实亡；发起人自选(35)靠引擎旁路侥幸工作。→ 补 nodeKey+提交 API 入参，或删除选项。

**P2（9）**
- AI 决策通过后 executeTask 走人工通道过权限校验，操作者非候选人时自动通过静默失败（AiApprovalExecutor.java:95-165，疑似——取决于候选人配置）→ 统一走 autoCompleteTask。
- ReliableEventConsumer：审计自身失败即事件彻底丢失（仅 error 日志）；单线程 retry 串行所有监听器重试（:65-101）。
- AuditLogEventHandler 用 @EventListener 非 AFTER_COMMIT + @Async 无限定符（幽灵审计 + 回退无界 SimpleAsyncTaskExecutor，:32-66）。
- 超时提醒无去重：autoComplete 失败时每 60s 重发"紧急"通知轰炸（WorkflowTimeoutJob.java:163-178）。
- 提醒体系三套参数漂移 + 引擎 remind() 链路死代码（TaskReminderHandler.java:174-250）。
- **触发器 SpEL 用 StandardEvaluationContext + Class.forName 任意实例化**（TaskTriggerHandler.java:142-150,198-201）— 建模可控即 RCE 面（实际取决于模型编辑权限边界）。→ SimpleEvaluationContext/包前缀白名单。
- AI 审批 prompt 用户表单值原文直拼（注入面）+ isSensitiveField contains("key") 过宽误滤（WfAiApprovalServiceImpl.java:85-112,237-241；路由同构）→ 定界符包裹+精确名单+默认 fallback=MANUAL。
- 两个 AI 服务四套工具方法+超时模板重复（WfAiRoutingServiceImpl/WfAiApprovalServiceImpl）→ 抽 WfAiPromptSupport。
- FlowLongDiagramGenerator drawNode 14 段 else-if 同签名分发（:436-485）→ NodeStyle 表驱动，可删 ~150 行。

**P3（6）**
- FlowLongWorkflowConfig 空壳死代码（注释还引用已删组件）；FlowLongSchedulerConfig 冗余 @Bean + JobDataMap 死数据；FlowLongSchedulerProperties 三属性无消费方（useDistributedLock/lockType/reminderAdvanceMinutes）。
- 管理员越权办理返回 taskActors.get(0) 顶包，办理留痕归到无辜首位候选人名下（ForgeTaskAccessStrategy.java:77-89）。
- convertToTaskActors 死代码+actorType 硬编码 0（BpmTaskCandidateInvoker.java:93-104）。
- 全限定名/通配 import/双 Logger 穷举（WorkflowReminderJob/TaskReminderHandler/ApprovalTimelineBuilder/DiagramGenerator 等）。
- DiagramGenerator findNodeByKey 循环内递归 O(n²)；深度计算无环保护可 StackOverflow（:105-402）。
- 见代理报告（正则强转冗余等杂项）。

### 4.6 Workflow — im+callback+demo（25 条）

**P1（10）**
- **回调 secret 明文回传前端**（WfBusinessCallbackServiceImpl.java:162）— 见 §2-7。
- **回调 URL 无内网限制 + 响应体落库回显 = 读回型 SSRF**（CallbackDispatcher.java:116-117、CallbackRequest.java:23）— 可配 `http://127.0.0.1:8080/actuator/env`、169.254.169.254，响应 2000 字回显给配置者，resend 可按需重触发。→ 双层 URL 校验（环回/私网/链路本地+DNS 解析后复检）。
- **IM 租户来源塌陷**（ImChannelConfigService.java:47-50 + workflowExecutor 未包 TTL）— 见 §3-3。
- **回调模块未采用显式租户模式**（BusinessCallbackEventListener.java:118-127）— 见 §3-4。
- **wf_im_channel_config 缺唯一约束 + check-then-insert**（upgrade-20261110-im-notify.sql:27、ImChannelConfigService.java:87-97）— 并发双行后 `configOf` 的 selectOne 抛 TooManyResultsException（MP 3.5.18.1 默认，字节码核实），该租户全部 IM 推送×4 重试失败且 **UI 无法自愈**（upsert 也先 configOf）。→ 唯一约束（注意 @TableLogic 占键）+ DuplicateKey 重查。
- **回调重试内联 sleep 占共享 workflowExecutor + CallerRuns 反噬审批主线程**（CallbackDispatcher.java:63-74 + WorkflowAsyncConfig.java:17-30）— 单事件最长 12s sleep+3×2×timeout（timeoutMs 无上限可配 600000）；队列满后 CallerRuns 让**审批请求线程**同步执行投递。→ 独立回调池+scheduler.schedule 替代 sleep+timeoutMs 钳制。
- **最后节点 taskCompleted 查不到已归档实例 → processKey=null**（BusinessCallbackEventListener.java:73-77）— 引擎 endInstance 先删 flw_instance；按流程 key 订阅 taskCompleted 的业务方**永远收不到最终节点办结事件**（E2E 只测了 processKey=null 回调未覆盖）。→ 事件携带 processId/processKey 快照。
- **发起人撤销被 IM 通知为"已被驳回"**（ImMessageBuilder.java:73-84 + WfProcessInstanceServiceImpl.java:588）— terminate 触发 rejectComplete 双语义，stateText 只按 eventType 分支；forceComplete 分支是死代码（1.2.7 全 jar 无发射方）。→ eventType+InstanceState 二维判断；demo 注释同修。
- **demo 回写仅凭 businessKey 前缀定位，无归属/CAS**（ProjectWorkflowEventHandler.java:40-80）— 见 §2-8。
- **wf_event_audit 无 tenant_id 且不在 ignore-tables**（init-workflow.sql:682-699）— 见 §3-5。

**P2（11）**
- token/unionid 缓存 key 不含凭据指纹：换 corpId/secret 后最长 2h 用旧凭据发旧企业（AbstractTokenChannel.java:24-45）。
- compute 内网络调用持 bin 锁；forceRefresh 并发风暴；误报日志；空 token 被缓存（AbstractTokenChannel.java:34-43）。
- ImUserBindingResolver 查 sys_social_user 未走显式租户模式（模块设计漏网之鱼，:97-102）。
- **deleteConfig 是唯一未显式传租户的 @TenantIgnore 方法；upsert/list 的 tenantId 完全客户端可控且无平台管理员校验**（WfImConfigController.java:42-63、ImChannelConfigService.java:138-142）— 菜单一旦进租户包即可劫持他租户 IM 配置（条件性）。
- AuditLogEventHandler @EventListener 非 AFTER_COMMIT + @Async 无限定符（同 4.5 条，两代理独立发现）。
- demo 状态回写消费未提交事件：发起即终态的流程回写落空静默丢弃/submitProject 或ElseThrow 必 500（ProjectWorkflowEventHandler.java:38-40 + ProjectApprovalServiceImpl.java:89-92）。
- resendRecord 在 web 线程同步投递（含 12s sleep）+ 原记录不更新可无限重发（WfBusinessCallbackServiceImpl.java:108-123）。
- 出站 payload 复用 web 全局 ObjectMapper：Long→String 契约随数值大小漂移（单测用 new ObjectMapper 断言数字——测试契约≠生产契约）；task 事件 businessKey 恒 null；无 eventId（at-least-once 下接收方无法幂等）/tenantId（CallbackPayloadBuilder.java:31-73 + JacksonConfig.java:43-44）。
- EncryptTypeHandler 解密失败非 fail-closed：一条坏密文瘫掉该租户全部渠道+管理页（UI 无法自愈）；"ENCv1:" 前缀输入透传可被投毒触发持久 DoS（EncryptTypeHandler.java:30-46）。
- AES 密钥零填充/截断无 KDF 无最小长度；base yml 内置默认密钥（CryptoUtils.java:69-78 + application.yml:187）。
- 升级脚本：project-example 声称幂等但 ALTER 非幂等（全新库首跑即断，菜单不执行）；callback 脚本 `USE forge_admin`（违规范 §5.1）+ `DROP TABLE IF EXISTS`（误重跑清空全部回调配置与审计）。

**P3（4）**：CallbackSigner/Dispatcher 正文 FQN；IM/回调/demo 写端点缺 @OperationLog、pageSize 无 @Max、DTO 落位偏离规范的合并穷举；死代码 getRecord + update 危险默认值（enabled 缺省→1 静默重启用等）+ 每次尝试 new RestTemplate；demo 状态映射杂项（魔数 5 应引用枚举、timeoutComplete→APPROVED 口径冲突、注释 revokeComplete 错误传染）。

### 4.7 system 模块（16 条）

**P1（3）**
- **套餐删除/停用漏挂 userAuth 缓存失效**（SysTenantPackageServiceImpl.java:170-197）— add/update 都调 evictAll，delete/updateStatus 只清 menu；权限收敛最长 5 分钟越权窗口；且不校验套餐仍被引用。→ 补 evictAll + 引用校验。
- **selectByUsernameSimple 不支持 tenantId=null**（SysUserMapper.xml:64 + JwtAuthenticationFilter.java:156 + SocialAuthServiceImpl.java:337）— SQL 硬编码 `tenant_id = #{tenantId}`，null 永不匹配：OAuth2 服务账号映射与社交登录两条认证路径**实际不可用**（社交登录签发的 token 后续接口全 401）。→ `<if>` 分支或 IS NULL。
- **微信登录 Mock 无环境守卫**（AppAuthServiceImpl.java:189-196）— 见 §2-5。

**P2（7）**
- `/system/config/key/{key}` 无权限注解：任意登录用户可枚举读全部启用配置值（SysConfigController.java:41-44）→ 公开键白名单。
- 验证码明文进 warn 日志（expected+actual 双打）+ 4 位码 300s 内无限试、失败不删码（CaptchaServiceImpl.java:73-76）。
- importUsers 缺 fillSearchSuffixes：导入用户手机/邮箱搜索永久失效（SysUserServiceImpl.java:643-693）。
- deleteRole 不清 sys_user_role/sys_role_dept 孤儿行；deleteTenants 数据完全残留不踢会话（SysRoleServiceImpl.java:150-166、SysTenantServiceImpl.java:225-231）。
- OAuth 回调 token 放 URL query（进历史/日志/Referer）+ **state 从不校验**（CSRF 缺失）+ 不强制首登改密（SocialAuthController.java:53-63）。
- 微信 jscode2session 裸 new RestTemplate 无超时每请求新建（AppAuthServiceImpl.java:197-200）。
- Quartz RAMJobStore + 每节点全量加载：多实例部署必然重复执行（QuartzConfig.java:38，疑似——取决于部署形态）。

**P3（6）**：正文 FQN 穷举（AuthController/SysUserServiceImpl/JobNotifyService 等）；邮件通知 TODO 空壳静默不发+webhook 手拼 JSON；QuartzJobExecution 每次新建线程池/重试 sleep 占调度线程/统计读改写非原子；Dashboard/simple-list/list-by-biz 三端点无权限码；updateStatus/updateProfile/updateAvatar 缺 @Transactional；getPackageAllowedMenuIds 每次全表扫 sys_menu。

**权限审计正面结论**：10 个控制器写端点 @PreAuthorize+@OperationLog+@RepeatSubmit 三件套齐全；App 端无水平越权路径（@AssertAppUserActive 切面+过滤器注入 userId 不可伪造）；敏感读端点（用户列表/导出/租户/审计日志）权限码全覆盖；UploadsController 匿名读为"URL 即凭证"设计取舍（32 位 UUID 不可枚举，路径穿越已防）。

### 4.8 framework + screen + forge-server（34 条）

**P1（9）**
- **默认链 permitAll**（SecurityConfig.java:174-183）— 见 §2-2。
- **prod actuator 配置失效**（application-prod.yml:43-51）— 见 §2-2。
- **CORS `*`+allowCredentials+Cookie 取 token+CSRF 关闭组合链**（SecurityConfig.java:186-197 + JwtAuthenticationFilter.java:187-204）— 恶意站点可携 Cookie 跨站认证请求；`forge.security.cors-allowed-origins` 配置项是无人消费的死配置（SecurityProperties 从未注入）。→ 配置白名单+Cookie 路径补 CSRF 防护。
- **大屏执行层用原始模板击穿 C4 注释剥离契约**（SqlDataSourceExecutor.java:71-92）— 见 §2-6。
- **表/列白名单只覆盖主表 FROM 与 SELECT 简单列**（SqlSafetyGuard.java:92-121）— JOIN 表无表级校验、WHERE/ORDER BY 列无列级校验（布尔盲注/排序推理面）；`requestedColumnsByTable` 恒传空 Map。→ TablesNamesFinder 全量 + Column 节点全树遍历。
- **注入的 RestClient Bean 无超时，http-timeout-ms 全失效**（ScreenAutoConfiguration.java:30-33 + HttpDataSourceExecutor.java:208-216）— buildClient 带超时分支成死代码；慢响应挂住线程+叠加全局 singleflight 锁拖垮大屏。→ Bean 内配超时工厂。
- **LIMIT 占位符改名即无界拉取（OOM）**（SqlDataSourceExecutor.java:116-139）— 见 §2-6。
- **角色授权大屏对所有人恒 403**（SysScreenServiceImpl.java:99-124）— 反射 `principal.getRoleIds()` 必失败（principal 是 Spring Security User），回退解析 `role:` 前缀 authority 也恒空。→ 从 UserContext.get().getRoles() 取。
- **update() 直接覆盖已发布 config**（SysScreenServiceImpl.java:145-162）— 保存草稿立即改线上渲染配置，publish 两阶段语义作废；update 不校验存在性、@Version 恒 null。→ 只写 configDraft。

**P2（15）**
- 600+ 行自研数据权限拦截器死代码（DataPermissionInterceptor×2），且与 MP 官方同名类 import 遮蔽误导（MybatisPlusConfig.java:7,57）→ 删除。
- WebSocket /ws 任意源+无 STOMP 鉴权拦截器+permitAll：可匿名订阅 /topic/notifications 广播（WebSocketConfig.java:29-30）。
- XSS 过滤器黑名单正则可绕、不覆盖 JSON body、每请求双跑 handler 解析（XssHttpServletRequestWrapper/XssFilter）。
- IP 限流信任可伪造 XFF；用户名提取失败归入共享 unknown 桶（公共桶 DoS）；Lua 注释"令牌桶"实为固定窗口（RateLimiterAspect.java:114-163）。
- Redis 缓存 ObjectMapper 开 NON_FINAL 全量默认类型+Object 基类型 PTV（CacheConfig.java:59-72）— Redis 可写即 gadget RCE 面（纵深防御项）。
- prod 日志滚动用了 Boot 4 已改名旧键静默失效（application-prod.yml:93-96）→ `logging.logback.rollingpolicy.*`。
- screen 四表无 tenant_id 不在 ignore-tables：开租户全线报错（V202607041__create_screen_tables.sql）→ 见 §3-6。
- 熔断器把用户侧 4xx/参数错误也计失败：1 分钟 10 次坏请求熔断数据源 30s 误伤正常用户（SysScreenDataSourceServiceImpl.java:113-139）。
- "dept" 在 ignore-caches 但 sys_dept 是租户表：配置与数据模型矛盾（疑似——未见写入点）。
- HTTP 数据源默认跟随重定向可绕 host 白名单（HttpDataSourceExecutor.java:126-136，与 AI HttpFetchTool 同型）。
- base yml 内置 JWT/AES 默认密钥：漏配环境变量的部署可被仓库读者伪造 token/解密 ENCv1（application.yml:187,268）→ 删默认值 fail-fast。
- TenantRedisCacheManager 租户前缀烘焙在缓存实例创建时刻（"首租户独占"串台，见 §3-8）。
- 全实例单一 ReentrantLock 串行所有数据源缓存加载（DataSourceCacheService.java:41-83）→ per-key 合并。
- 危险函数扫描遗漏 ORDER BY/GROUP BY 表达式（`ORDER BY SLEEP(1)` 不拦截，SqlSafetyValidator.java:163-194）；UNION 递归只查 from/join（TablesNamesFinder 兜底故降级）。
- Jasypt 用 PBEWithMD5AndDES 弱算法 + 注释建议 -D 传密钥（进 ps 输出）（application-prod.yml:168-171）。

**P3（10）**：正文 FQN 穷举（AppJwtAuthenticationFilter/SysScreenServiceImpl 等）；各处 `new ObjectMapper()` 绕开全局 Jackson 定制；CacheConfig keyGenerator 参数拼接无分隔符可碰撞；GlobalExceptionHandler 缺 HttpMessageNotReadable/TypeMismatch→畸形 JSON 报 500；种子 SQL 数据源自身不满足安全闸开箱即坏；app JWT 每请求解析两次+parseLong 异常顺序；CryptoUtils 短密钥零填充/"ENCv1:"前缀透传；SqlSafetyException 被包成 BusinessException 专用 400 映射失效；实体注释与实现矛盾（columnList）；`/admin-api/userinfo` 疑似死白名单条目。

---

## 5. 跨模块共性问题（重构时按主题批量处理）

1. **资源归属校验（横向越权）缺失** — AI 会话/文档/运行记录、workflow 任务动作/意见附件、demo 回写、IM 配置跨租户。共 ~10 处，修法同型：`requireOwnedXxx` + 管理员豁免。**最大单一主题。**
2. **多租户开闸雷区** — §3 表 8 项；根因两类：异步线程租户上下文（TTL 未包装/事件无快照）与存量表缺 tenant_id 未治理。
3. **异步线程池系统性欠账** — 单池多角色（agentExecutor 四角色/workflowExecutor 承载 AI+IM+回调）、queue 大 max 失效、commonPool 滥用、CallerRuns 反噬 web 线程、@Async 无限定符回退无界 SimpleAsyncTaskExecutor、join 无超时。
4. **外部调用无超时** — 裸 new RestTemplate/RestClient.create() 共 4 处、chat() 无超时、回调 timeoutMs 无上限、HTTP 数据源超时配置失效。
5. **check-then-act 竞态** — 催办节流、待办审批双恢复、IM config upsert、generateTitle、todo 审批。修法同型：条件更新（`WHERE status=旧值`）判受影响行数。
6. **死代码/死配置积累** — 自研数据权限拦截器 600 行、FlowLongWorkflowConfig、convertTaskToResponseWithProcess 单参版、引擎 remind() 链路、ai.client.connect-timeout、useDistributedLock/lockType、SecurityProperties、forceComplete 分支。建议每季度一次 `grep -r` 清理批次。
7. **手工字符串拼 JSON** — AiChatController 错误帧、AgentToolCallback schema/错误、JobNotifyService webhook。全库统一改 ObjectMapper。
8. **全限定名屡清屡犯** — 06a9603/e23354c/2f69b65 三次专项清理后 8 个模块仍有残留 → **规范已失效，需要 ArchUnit/Checkstyle 守卫规则**（编译期拦截正文 FQN）。
9. **重试/兜底静默吞错** — 专家子图 InterruptedException 无痕、ReliableEventConsumer 审计失败即丢、记账失败仅 warn。兜底可以，但必须有计数器/告警指标。
10. **密钥管理双标** — IM secret AES-GCM vs 回调 secret 明文；prod 强制环境变量 vs base 内置默认密钥；Jasypt 弱算法。统一密钥管理规范。

---

## 6. 值得保留的做法（避免重构时误伤）

1. **SqlTenantRewriter（AI）教科书级 fail-closed**：解析失败即拒、租户缺失即拒、注入后全量后验、标识符归一化，CTE/派生表/JOIN ON 递归覆盖——问题只在 schema 前缀一处（§4.3）。
2. **mapper XML 对 jsqlparser 4.9 × 租户插件坑的显式防御**（保留 VALUES() 避免 AS new 行别名）+ AiMapperXmlGuardTest 守卫——技术栈事实链闭环。
3. **apiKey 全生命周期管控**：AES-256-GCM 落库、启动幂等迁移、9 个出口统一掩码、日志零泄漏（grep 实证）。
4. **FlowLong 引擎坑位注释级对齐**：done 列表排除 taskType=0、isHumanApprovalTask 覆盖转办/委派 taskType 改写、取消=terminate→rejectComplete(5) 语义正确、use_scope 反序列化坑有注释+测试护栏。
5. **system 缓存失效设计**：doEvictAfterCommit 正确处理"事务内先失效被旧数据回填"；密码体系（BCrypt/历史防复用/首登改密）完整；手机邮箱加密+可检索 suffix 设计巧妙。
6. **AgentRunEventBridge / EXPERT_DONE 标记 / AgentDataCleanupService 分批双保险**：AI 编排的工程质量点。
7. **wf_callback_record 审计闭环**（成功也落库、真实 HTTP 状态保留）与 **E2eCallbackDeliveryTest 内嵌 HttpServer 三段端到端**。
8. **RepeatSubmitAspect Lua SET NX EX / JwtAuthenticationFilter 租户切换纪律 / HttpDataSourceExecutor 流式限体读取**。
9. **Task 侧四列表 N+1 已治理**（BatchContext 批量+测试守护）——问题只是 Instance 侧没同步。
10. **权限注解纵向覆盖**：AI 43/43、system 10 控制器三件套、App 端切面防水平越权。

---

## 7. 修复路线图建议（按投入产出排序）

**第一批·安全堵口（~1-2 天/人）**：§2 清单 1-8（归属校验×3 组、SecurityConfig/actuator、审批残留、Mock 后门、大屏 SQL 三闸、secret 回传、demo CAS）+ 触发器 SpEL 收敛 + CORS/密钥默认值。
**第二批·多租户开闸前置（~2-3 天）**：§3 全部 8 项（事件租户快照 + TtlExecutors + 表治理 + 插件顺序断言）。若近期不开租户可降级，但 §3-3/3-4 的租户串扰在单租户下无感、修复窗口随时可用。
**第三批·稳定性（~2 天）**：流断连取消感知、竞速门控、chatForDecision 改造（顺手关掉 45s 悬案）、线程池隔离（agentExecutor/workflowExecutor/回调池）、Redis 锁令牌化、各类外部调用超时、无条件更新改 CAS。
**第四批·上帝类拆分（~3-5 天，可与前三批并行）**：Instance/Task 按既有测试护栏拆（WfProcessModelValidator 先行——现有反射调私有方法的测试可转正）；AI 两 Graph 抽 GraphSupport；DiagramGenerator 表驱动。**每拆一块跑既有 H2 E2E 套件**。
**第五批·卫生批次（~1-2 天）**：死代码/死配置清理、FQN ArchUnit 守卫、手工 JSON 改 ObjectMapper、@OperationLog 补齐、readOnly 事务、升级脚本幂等修正。

**流程建议**：按 FlowNeo 重任务立项分批执行；第一批安全项建议合并为一个"安全加固"任务优先走完五阶段。

---

## 8. 复核说明（汇总人亲核）

以下 6 项最重结论由汇总人直接读源码二次确认，均与代理报告一致：

| 结论 | 复核位置 |
|---|---|
| 默认链 `anyRequest().permitAll()` | SecurityConfig.java:181（CORS `*`+credentials 同文件 :188-191 一并确认） |
| prod `management:` 缩进在 `spring:` 下 | application-prod.yml:44 |
| 权限拦截器 TODO 空转 | PermissionTaskCreateInterceptor.java:65-80 |
| AI 会话无归属校验 | AiChatServiceImpl.java:64-117（page/getMessages/updateTitle 三处直查） |
| 审批残留绕过高危门 | ConversationalAssistantGraph.java:334-341（`isPresent()` 即放行） |
| rejectTask 仅存在性校验 | WfTaskServiceImpl.java:681-706（仅 validateTask） |

引擎侧结论（isAllowed 缺失、endInstance 删表、terminate 双语义、MP selectOne throwEx）由评审代理经 flowlong-core-1.2.7.jar / mybatis-plus 3.5.18.1 字节码（javap）核实。其余各条的"疑似"标注均已在正文注明缺何种运行时证据，未与实证结论混排。

**评审分工**：AI client 层 / AI agent 编排 / AI service+tools（安全优先）/ WF 两大 ServiceImpl / WF framework 扩展点 / WF im+callback+demo / system 标准扫 / framework+screen+server 标准扫，共 8 个评审单元。

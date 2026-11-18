# 更新日志

本项目所有显著变更都记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## [2.3.0] - 2026-11-18

### 新增

- **审批侧业务单据视图（formType=20 业务表单做实）**：`WfBizViewProvider` SPI（workflow-api）——业务模块按 businessKey 前缀路由提供单据只读视图（结构化字段 + 附件清单 + 可选 PDF 预览），审批抽屉/流程详情内嵌「业务单据」卡；三个平台代理端点统一「实例参与人」鉴权（发起人/办理人/抄送人/工作流管理员），审批人无需业务模块查看权限，附件跨单据探测返回 404；SPI 缺失/异常/单据已删自动降级为「申请数据」实例变量快照兜底（无表单流程也一并受益）；contract（字段+附件+合同 PDF 预览）与 demo 项目立项（纯字段）首批接入，合同默认模型部署自动携带 formType=20；建模端选「业务表单」给出接入提示并清空流程表单关联；审批详情打印纳入业务单据节（单据标题+字段+附件清单，快照兜底同口径，后续节序号顺延）

- **附件体系重构（A/B/C/D 四批）**：存储 SPI 补 `download` 读能力 + 上传签名去 `MultipartFile` 化（面向流），`SysAttachmentService` 新增内部流式上传与 `loadContent` 统一读附件；`AttachmentBizTypes` 常量统一 7 种业务类型（存量 `APP_AVATAR`/`brand-logo` 归一迁移）；`AttachmentRefChecker` 引用校验 SPI——公告/审批评论/合同/合同模板/AI 文档五方注册，删除被引用附件将被拒绝并提示引用方；合同模板保存时图片附件按内容自动绑定/解绑 bizId；附件孤儿回收定时任务（最小白名单 contract/wf_comment/notice、未绑定超 7 天，sys_job 种子默认暂停）；`list-by-biz` 端点补 `system:attachment:query` 权限；`fileConfig` 缓存进租户 ignore-caches
- **合同备注字段**：`ct_contract` 补 `remark`——任意状态（含审批中/已定稿）可改的管理性信息，不参与渲染与定稿快照；合同列表/表单/详情展示，详情内独立修改端点
- **数据字典转换统一出口（后端码值渲染）**：`DictLabelUtils` 静态工具（system-biz）——字典码值→label 解析三级链路：本地 Guava 缓存（60s TTL、同 key 并发去重、空字典负缓存）→ Redis（1h）→ DB，`resolve` 由每次一次 Redis 往返 + 整表反序列化降为 JVM 内 O(1)，异常降级不缓存；字典写路径（增删改/状态/批量保存/手动刷新）即时失效本地缓存，多实例 ≤60s 收敛；合同业务单据视图与 demo 项目立项状态 label 首批接入（业务侧不再硬编码 switch/if 映射）；`contract_status`/`demo_project_status` 两状态字典种子，前端合同状态渲染同步切到字典

### 修复

- **全新部署上传即炸**：`sys_attachment.file_config_id` 列从未进过任何 SQL（init.sql 已内联，含 tenant_id 一并内联）
- **编辑文件存储配置毁密钥**：脱敏 `ABCD****` 回写覆盖真实 AccessKey、空 SecretKey 清空库中原值（空值/掩码跳过覆盖，编辑表单留空占位「留空则不修改」）
- **顶栏布局菜单过多溢出遮挡右上角功能区**：el-menu 开启 ellipsis 溢出折叠进「…」子菜单；补 element-plus 盲区——定宽容器下 ResizeObserver 不触发，异步菜单到达后手动调 `handleResize` 重算折叠
- AI 文档解析直读本地盘的硬耦合（改走 `loadContent`，minio/oss 默认存储下不再必然失败）；App 上传校验与全局策略两套口径并轨（错误码 5801/5802 不变）；附件服务 `RuntimeException` 全量换 `BusinessException`
- **业务单据预览/附件下载报「请求失败」**：`getBlob` 调用漏传 `responseType`，二进制被 axios 拦截器当 JSON 处理（已改为默认携带，新调用免传）
- **业务单据预览弹窗只显示顶部一条**：append-to-body 传送门下 scoped `:deep` 样式失效
- **平台超管切换租户后被回写覆盖**：切换租户的身份被后续请求回写还原

### 变更

- 合同定稿 PDF 上传改流式直传，`InMemoryMultipartFile` 适配器删除
- 合同详情从抽屉改为对话框并优化布局；合同模板列表增方向列；定时任务对话框表单布局优化
- 审批打印无表单数据时整节隐藏（去掉「本流程未配置表单」占位）
- **升级脚本（存量库须手工执行，幂等可重复跑）**：`sql/upgrade-20261117-attachment-refactor.sql`、`sql/upgrade-20261117-contract-remark.sql`、`sql/upgrade-20261117-biz-view.sql`、`sql/upgrade-20261118-contract-status-dict.sql`、`sql/upgrade-20261118-project-status-dict.sql`

## [2.2.0] - 2026-11-16

### 新增

- **插件体系全量落地**：编译期模块插件架构，平台总闸 + 租户级启停（停用后菜单隐藏、数据保留）；License 离线授权（三要素校验 + 到期巡检自动停用）与在线签发双模式；每插件可切换「免费开放 / 收费授权」双收费模式，新租户自动启用免费插件；插件数据权限 scope 勾选式授予；插件管理页卡片式布局；`create-plugin` 脚手架指令一键生成完整插件骨架；配套《插件模块手册》《插件开发指南》文档
- **合同模板管理模块**：tiptap3 在线富文本编辑（工具栏图标化、高亮 5 色、字号档位、对齐/删除线/引用/水平线/清除格式），变量芯片插入；纯 Java PDF 渲染（openhtmltopdf），PDFBox 3 水印升级 3×3 平铺小字 + 文案带日期，实时预览同样盖章；FlowLong 审批集成——审批中可预览打印（带『审批中』水印），定稿走直链；纸张 A 系列 + 信函 + B3、横竖方向、页边距三档（窄/正常/宽，Word 同口径）编辑器内可直接调整且渲染全链路生效；编辑器分页参考虚线 + 页码角标；编辑合同弹窗免保存预览；新建/编辑合同可手填合同编号（租户内查重，留空定稿自动生成）；PDF 预览独立全屏弹窗
- **合同分类树形化**：`ct_category` 树表 + 列表页左树筛选 + 仅叶子可挂模板，原分类字典平滑迁移停用

### 修复

- **附件直链 iframe 预览全链路打通**（四层连环）：Spring 7 路径属性口径导致 404、租户闸放行缺失、uploads 安全链默认 X-Frame-Options=DENY 拒绝跨源 iframe
- **PDF 渲染系列根治**：表格行高暴涨（单元格隐形空行）、列表序号与内容同行、空格渲染不丢失、回车空行保留整行高、表格单元格对齐丢失与编辑器回显、预览乱码（端点显式 `produces=application/pdf`）
- **框架**：未匹配路径/方法/媒体类型异常误报 500，补 404/405/415 语义处理
- 合同模块：编辑合同 Incorrect string value（Wrapper.set 显式 typeHandler）、修改模板方向/边距不生效、保存模板版本误报「模板ID不能为空」、新建合同 Unknown column、preview-draft 端点改动静默丢失
- 插件：租户授权抽屉改以租户为主表展示（全新租户无启停记录也能发起首次启用）

### 变更

- 编辑器排版重做：白纸整页模型（padding 与 PDF 页边距同口径）撑满画布，保存操作挪右侧面板，纸张设置卡单列对齐；编辑器与 PDF 表格/列表/标题样式双向统一，所见即所渲染；思源字体仅 PDF 内嵌，编辑器退回系统字体（前端省 41MB）
- 插件 License 有效期展示精确到天，签发弹窗改按天选择（到期日收口 23:59:59）；列表左侧分类树去掉快捷新增入口，分类维护统一走分类管理页
- **升级脚本（存量库须手工执行）**：`sql/upgrade-20261113-plugin-module.sql`、`sql/upgrade-20261115-plugin-charge-mode.sql`、`sql/upgrade-20261116-contract.sql`、`sql/upgrade-20261116-contract-orientation.sql`、`sql/upgrade-20261116-contract-margin.sql`、`sql/upgrade-20261117-contract-category.sql`

## [2.1.0] - 2026-11-13

### 新增

- **审批消息推送企业微信/钉钉工作通知**：任务待办/催办/超时/实例终态事件自动推送；配置按租户独立存储、密钥加密入库、接口不回显；个人中心新增企业微信绑定入口，管理端新增 IM 通知配置页（租户维度管理双渠道）
- **会话标题 AI 自动生成**：对话首轮回复完成后按首条消息生成短标题，手动改名受保护
- **AI 思考过程流式展示**：思考模型的 reasoning 内容在聊天/Agent 页实时透传展示，消除思考期反馈空窗
- **系统配置分组筛选**：列表页新增分组下拉筛选（选项为库内去重分组），新增 `GET /system/config/groups` 接口
- **OAuth2 客户端密钥生成规则收紧**：新生成密钥统一为 `forge` 前缀 + 27 位随机字母数字（总长 32 位、不含连字符），创建与重置密钥两处生效
- **easy-trans 数据翻译框架集成**：替代手动查询翻译字段，流程模型列表新增关联表单列
- 模型管理新增「禁用思考」状态列

### 修复

- **AI 流式稳定性系列**：打字机效果丢失根因修复（Flux/MVC 消费组合唤醒丢失）、流式客户端多 IP 节点容灾与坏边缘节点免疫、流式节点健康判定补强（中段停顿检测+冷却记忆）、发起 2 秒无首帧显示「连接模型中」排队提示、响应头超时可配置且放宽至 120s、SSE 端点 ASYNC dispatch 误报拒绝访问、深度思考内容 Markdown 渲染、流式 URL 双重后缀 404 与 MVC 异步执行器未生效
- **45 秒决策超时根治**（稳定性批次）：流断连取消、竞速门控、线程池隔离、锁令牌化、外部调用超时、CAS 四处
- **多租户开闸**：TTL 线程池租户透传、事件租户快照、IM 回调显式租户、双 Job 租户遍历、插件顺序与 ignoreInsert 防重列等前置适配，租户开关默认开启；菜单递归 CTE 名被租户插件误追加租户列导致菜单接口报错的修复
- **安全加固批次**：资源归属校验、默认链 fail-closed、大屏 SQL 三闸、审批门控、密钥外化
- **状态切换误报「请勿重复提交」**：定时任务等 6 类幂等状态切换端点摘除防重注解，el-switch 快速来回拨不再被 5 秒锁窗拦截
- 催办与待办通知 AFTER_COMMIT 化（事务回滚不再产生幽灵通知）、IM 配置逻辑删除墓碑惯用法对齐、处理任务抽屉受理人与列表口径对齐
- **认证性能**：JWT 认证路径接入 Redis 用户上下文缓存，命中路径零 DB，权限变更即时生效

### 变更

- 后端开发规范 v1.1：新增并发/事务/逻辑删除卫生等 20 条条款，代码正文禁全限定名
- 工作流上帝类拆分、候选人策略租户解析四副本收敛、jQuery 退出全局启动路径（打印组件纯 DOM 挂载）、SockJS 弃用改原生 WebSocket
- **升级脚本（存量库须手工执行）**：`sql/upgrade-20261110-im-notify.sql`、`sql/upgrade-20261111-ai-reasoning.sql`、`sql/upgrade-20261112-im-unique-tenant-channel.sql`、`sql/upgrade-20261112-tenant-wf-event-audit.sql`

## [2.0.0] - 2026-11-08

### 变更

- **Spring Boot 3.4.11 → 4.1.2 全链路升级**（Spring Framework 7 / Security 7 / Tomcat 11 / JUnit 6，479 项测试全绿，应用 7.7s 启动）：
  - Jackson 2 → Jackson 3（tools.jackson）全量迁移：66 文件、9 个自定义序列化器 SPI 重写，序列化契约（Long→字符串、LocalDateTime 秒级时间戳）保持不变；实体 JSON 字段统一 `Jackson3TypeHandler`
  - Spring AI 1.1.8 → 2.0.2：`OpenAiApi` → 官方 OpenAI Java SDK（OkHttp 传输），重试改 SDK `maxRetries=2`；Spring AI 2.0 起 ChatModel 不再内部执行工具，与项目手动工具执行模式语义一致
  - Spring Authorization Server 并入 Spring Security 7（7.1.2），授权服务器链显式端点匹配
  - MyBatis Plus 3.5.18.1（boot4 starter，jsqlparser 4.9 锁定保持，租户/数据权限不受影响）；springdoc 3.2.0；ArchUnit 1.5.1
  - 删除死依赖：resilience4j、mockito-inline、jackson-datatype-jsr310、byte-buddy 覆盖
  - jasypt 维持 3.0.5（CVE-2026-9370 修复版未发布，发布后单独升级）
  - ArchitectureTest 两条存量失败修复：workflow→ai 耦合白名单补充 AI 路由、Service Impl 命名规则改为只约束顶层类

### 新增

- **角色管理分配用户**：对标分配菜单，抽屉分页勾选用户（跨页保留勾选、回显既有分配），覆盖式重写 `sys_user_role`；新增 `GET /{id}/user-ids` 与 `PUT /{id}/users` 端点，带存在性与租户越权校验、GRANT 操作日志
- **首页我的待办任务卡片**：仪表盘新增待办卡片（最近 20 条不翻页），行内流程编号/紧急标识/标题，点击直达审批
- **模型管理设计抽屉化**：流程模型「设计」改为抽屉弹窗打开设计器，不再跳转整页
- **工作流标题规则增强**：`${}` 模板支持发起人/业务键/日期与表单变量组合，实例列表可按标题搜索，任务节点可配置 title 覆盖；进程内事件消费可靠性提升（防丢失兜底）
- **品牌设置默认主题新增菜单底色配置**：BrandTheme 支持侧栏底色白名单字段
- **侧栏菜单底色浅/深档切换**：页面设置新增菜单底色档位，data-sidebar 维度 + CSS 变量级联
- **菜单按钮悬停自动展开**：折叠侧栏悬停展开菜单，移开自动收起

### 修复

- 浅色侧栏菜单悬停不可读（el-menu 颜色 props 传 var() 时 EP 内部推导失败回落纯黑底，改走 `--el-menu-*` 变量整链接管）
- 分配用户抽屉取消勾选不生效（后端 Long 序列化为字符串，勾选集用 `Number()` 归一）
- 角色/用户分配抽屉 el-table → vxe-table（勾选列/序号列/回显事件统一）
- 待办/已办流程名称搜索不生效（后端从未消费 `processDefinitionName` 的存量缺口）
- 各页面表格列的展示规范统一（对齐/宽度/格式）

### 性能与重构

- 字典标签表格首屏重复请求去重：`useDict` 模块级共享缓存 + in-flight Promise 去重，表格 N 行从 N 次请求降为 1 次；字典保存后已打开页面标签原地更新
- Mapper 注解 SQL 按规则迁入 mapper.xml（8 mapper/25 条），ArchUnit 新增第五组落位守卫（方法须绑定 SQL / 不混用注解与 XML / 注解限单行简单 SQL）
- 杂项清理：husky 残留、死代码删除；表格展示规范样式统一

### 部署提示

- 升级后首次发布建议清 Redis 业务缓存（dictData/dictType/sysConfig/userInfo/menu/dept）——Redis 序列化器换代（Jackson 3），新旧格式共存但建议冷启动
- 本次无新增 SQL 脚本，存量库无需执行迁移

## [1.1.0] - 2026-10-30

### 新增

- 工作流业务示例模块（forge-module-demo）：项目立项接入审批流"形态 A"完整实例——业务表经 businessKey 关联流程实例，发起与终态事件回写流程编号、审批状态与完成时间，含独立前端页面与菜单，可作业务模块接入工作流的最佳实践模板
- 工作流标题规则：模型配置 `${}` 变量模板（内置 processName/initiatorName/businessKey/date + 表单变量），发起时渲染存入实例变量，流程列表展示动态标题（详情与打印保持流程名称口径）
- 流程详情抽屉化：示例项目页「查看流程」与流程实例列表「详情」改为抽屉弹窗，复用实例详情组件，灰底与列表页观感一致
- 流程详情打印：实例信息 / 申请表单 / 审批记录三区块输出，申请表单两列排版与详情页对齐，打印时间取打印瞬间
- 审批进度默认收起：详情页与抽屉的时间线卡片点击头部展开
- 任务详情与流程详情补充紧急程度显示（字典标签渲染）
- 用户管理：账号锁定状态列（悬浮提示失败次数与剩余锁定时间，以 Redis 登录闸口为准）+ 锁定行一键解锁（二次确认）
- 用户管理：工具栏批量删除（勾选 + 二次确认，移动端保留单行删除）
- 模型管理：新增模型弹窗自动预填当前选中的分类（含子分类）
- 全屏菜单：持久化记住上次选择的菜单分类

### 修复

- 已办列表不再显示发起人自己的任务记录（排除发起节点）
- 流程/任务详情打开瞬间状态闪烁（加载中不渲染描述区）
- 用户列表锁定悬浮提示缺失失败次数与截止时间（分页查询补两列映射）
- 打印时间不显示（ISO 时间格式解析问题，改传毫秒时间戳）
- 打印发起人步骤动作正确显示"提交"，动作列改纯文本，审批意见保留换行
- 审批记录列顺序与禁用输入框文字颜色
- 工作流页面表格列宽显示优化

### 其他

- 已办列表「查看」复用流程详情抽屉，收敛到实例视角
- 紧急程度码值字典化（wf_urgent），前后端去硬编码
- 模型对话框表单改双列布局
- 工作流使用说明文档同步更新

### 存量库升级提醒

本次发布包含 3 个升级脚本，已有数据库按顺序手工执行（项目未引入 Flyway；脚本幂等可重跑）：

| 脚本 | 说明 |
|------|------|
| `sql/upgrade-20261029-project-example.sql` | 示例模块业务表 `demo_biz_project`、菜单（id 280-284）与超管授权 |
| `sql/upgrade-20261029-urgent-dict.sql` | 紧急程度字典 `wf_urgent`（常规/重要不紧急/紧急不重要/紧急且重要） |
| `sql/upgrade-20261030-title-rule.sql` | `wf_process_ext` 补 `title_rule` 列 |

全新建库直接执行 `sql/init*.sql`，无需以上脚本。

## [1.0.0] - 2026-10-28

首个公开发布版本，收录自项目启动以来的全部能力。

**forge-admin** 是一款开箱即用的企业级后台管理系统（Spring Boot 3.4 + Vue 3 + TypeScript，JDK 21），覆盖 RBAC 权限、B2B SaaS 多租户、工作流审批、可视化大屏、AI 对话与 Agent 编排、打印模板、等保二级合规。

### 系统管理

- RBAC 权限：用户/角色/菜单/部门/岗位五级管理，数据权限支持全部/本部门/本部门及以下/仅本人等多种范围（SQL 级过滤）
- 字典管理、参数配置、通知公告、在线用户（强制下线）、品牌设置（Logo/项目名/登录页文案运行时配置，全局生效免重构建）
- 定时任务：基于 Quartz 的任务管理与执行日志
- 操作日志 / 登录日志审计，服务监控（服务器/JVM/Redis）

### B2B SaaS 多租户

- SQL 级自动隔离（MyBatis Plus `TenantLineInnerInterceptor`）+ 缓存 key 租户前缀
- 套餐化菜单分配、平台超管 `X-Tenant-Id` 跨租户代管
- 创建租户自动生成管理员账号 + 强密码 + 首次登录强制改密
- 全库 16 表租户化（2026-10 路径 A 治理），`forge.tenant.enable` 一键回归单租户

### 工作流审批（FlowLong 1.2.7）

- 钉钉式可视化流程设计器：审批/抄送/条件/触发器/并行/包容/子流程/定时器/路由分支节点，模型部署与版本管理
- 办理方式：或签/会签/依次/票签（权重+门槛）
- 审批操作全集：通过/驳回（4 种驳回策略）/委派/转办/退回/撤回/加签/减签/认领/催办/拿回/唤醒，发起紧急程度
- 流程分类树（层级码编码、防环校验、前缀命中子树筛选）
- 可视化表单设计器、流程表达式管理
- AI 智能审批：节点级 AI 决策（通过/驳回/转人工），置信度阈值 + 失败降级，决策审计落库
- AI 智能路由：条件/包容/路由网关 AI 分支决策（同步限时 30s），降级链 SpEL 条件 → 默认分支，路由审计落库
- 业务回调 SPI：事件订阅外部系统，HMAC-SHA256 签名 webhook 投递 + 超时重试 + 投递记录审计

### AI 能力（Spring AI 1.1.8 + LangGraph4j 1.9.2）

- 多模型对话：直连 DeepSeek/Qwen/GLM/ERNIE（OpenAI 兼容协议），模型配置表驱动、可用性探测、主模型失败自动切备用
- SSE 流式响应；Tika 文档解析（PDF/DOCX/TXT）与智能摘要
- Agent 编排：工具决策循环（SQL 查询/HTTP 抓取）、高危工具人工审批中断（可恢复）、检查点恢复、执行时间线
- Agent 团队协作：Supervisor 调度式多 Agent，成员独立提示词/模型/工具白名单，流式汇总结论
- Agent 定时任务：cron 驱动后台自动化（单 Agent 或团队）
- AI 用量统计看板：请求量/成功率/Token/时延汇总与明细，按日期与模型过滤

### 可视化大屏（goView 1.3.2）

- 拖拽式大屏编辑器（iframe 嵌入管理端）、草稿自动保存、发布/版本管理、公开访问与登录访问
- SQL / HTTP 双数据源，缓存时长可配
- SQL 安全三道闸门：AST 校验（SELECT-only/禁 UNION/禁危险函数/必须 LIMIT）+ 表列白名单（列级 fail-closed）+ 运行时实参上限
- HTTP SSRF 防护（host 白名单/超时/响应体上限）、数据源熔断器（Redis 滑动窗口）

### 打印模板

- hiprint 拖拽式模板设计器（纸张/缩放/旋转/导出 JSON）
- 浏览器原生打印预览，按编号跨租户调用，附调用示例页

### 等保二级合规（GB/T 22239-2019）

- 密码安全：复杂度校验、历史 5 条不可重复、90 天有效期、首次登录强制改密、BCrypt=12
- 登录安全：失败 5 次锁定 15 分钟、图形验证码、单点登录（旧会话与 refreshToken 同步失效）
- 数据安全：AES-256-GCM 敏感字段加密、jasypt 配置加密、操作日志敏感字段自动脱敏
- 应用安全：XSS 过滤、安全响应头（CSP/HSTS 等）、文件上传扩展名 + Magic Number 校验

### 认证与第三方

- OAuth2 授权服务器（Spring Authorization Server，授权码/客户端凭证/OIDC）
- 微信、钉钉扫码登录（JustAuth）
- 微信小程序：独立 `/app-api` 端点与 `app_user` 用户体系，授权登录一键注册

### 主题系统

- 10 套预设套餐 + 调色板/布局/风格三维度独立切换 + 自定义 HEX 主色（自动派生 EP 色阶）
- 明暗双模式、无刷新切换（CSS 变量两阶桥接）、系统默认主题配置、老数据自动迁移

### 工程化

- pnpm monorepo：forge-web（管理端）/ forge-screen（大屏编辑器）/ forge-miniapp（小程序）+ 16 模块 Maven 后端（api/biz 分离、6 个框架 starter）
- 业务模块脚手架（创建/删除）、项目模板化工具（`pnpm run init` 一键派生新项目）
- Docker Compose 四服务编排（MySQL/Redis/后端/前端），首次启动自动执行初始化 SQL
- vxe-table 统一表格（列自定义/导出/打印/虚拟滚动/树形）

### 存量库升级（自早期内部版本升级时执行）

项目未引入 Flyway，已有数据库按日期顺序手工执行：

| 脚本 | 说明 |
|------|------|
| `sql/upgrade-20261024-ai-tenant.sql` | AI 4 表补 tenant_id |
| `sql/upgrade-20261025-tenant-full.sql` | 全库租户化：16 表补 tenant_id + 唯一键调整 |
| `sql/upgrade-20261025-ai-usage-menu.sql` | AI 用量统计菜单与权限 |
| `sql/upgrade-20261026-ai-route-record.sql` | AI 智能路由审计表 |
| `sql/upgrade-20261027-callback.sql` | 业务回调表 + 菜单权限 |
| `sql/upgrade-20261027-flowlong127.sql` | FlowLong 1.2.7 urgent 列迁移 |

全新建库直接执行 `sql/init*.sql`，无需以上脚本。

[2.3.0]: https://github.com/wlddhj/forge-admin/releases/tag/v2.3.0
[2.2.0]: https://github.com/wlddhj/forge-admin/releases/tag/v2.2.0
[2.1.0]: https://github.com/wlddhj/forge-admin/releases/tag/v2.1.0
[2.0.0]: https://github.com/wlddhj/forge-admin/releases/tag/v2.0.0
[1.1.0]: https://github.com/wlddhj/forge-admin/releases/tag/v1.1.0
[1.0.0]: https://github.com/wlddhj/forge-admin/releases/tag/v1.0.0

# 工作流模块文档

> 适用模块: `forge-module-workflow`
> 基于引擎: FlowLong 1.2.5（flowlong-spring-boot-starter，Boot3 兼容排除传递依赖）

---

## 文档清单(按阅读顺序)

| 序号 | 文档 | 目标读者 | 用途 |
|---|---|---|---|
| [01.workflow-usage.md](01.workflow-usage.md) | 使用说明 | 流程管理员 / 配置人员 / 最终用户 | 菜单导航、完整配置流程、分支节点选型、候选人策略、触发器使用、AI 审批与路由入口、待办/已办、状态码、迭代能力速览 |
| [02.condition-node-guide.md](02.condition-node-guide.md) | 条件节点指南 | 流程设计者 / 后端开发 | type=3/4 实际行为差异、SpEL 求值机制、变量缺失行为、兜底机制、AI 路由优先级、排查速查 |
| [03.trigger-bean-guide.md](03.trigger-bean-guide.md) | 触发器 Bean 开发指南 | 后端开发 | 自定义 @FlowLongTrigger Bean、AbstractFlowLongTrigger 抽象基类、4 种业务模式、反射调用 |
| [04.ai-approval-and-routing.md](04.ai-approval-and-routing.md) | AI 智能审批与智能路由 | 流程设计者 / 管理员 / 后端开发 | 两个 AI 能力对比、配置项、降级链与决策状态、审计表查询、注意事项与 FAQ |

## 推荐阅读路径

```
新接手流程模块?
  → 01.workflow-usage.md (§1-5 概览,§7 触发器任务)

要配置条件路由?
  → 02.condition-node-guide.md (§2 type=3/4 行为差异,§3-4 处理流程)

要让 AI 参与审批/分支决策?
  → 04.ai-approval-and-routing.md (§1 先看两者区别)

要扩展触发器能力(写自定义 Bean)?
  → 03.trigger-bean-guide.md (§4 bean 模式,§7 抽象基类,§8 返回值约定)

排错?
  → 02.condition-node-guide.md §9 排查速查
  → 03.trigger-bean-guide.md §10 调试与日志
  → 04.ai-approval-and-routing.md §5 AI FAQ
```

## 文档约定

- 所有文档之间的相对引用用 `[名称](序号.文件名.md)` 形式,例如 `[02.condition-node-guide.md §2.2](02.condition-node-guide.md#22-作为独立节点--条件不生效)`
- 编号用于**目录排序**和**明确优先级**,新文档按主题选下一个可用编号

## 暂未覆盖

以下内容暂未形成独立文档,需要查代码或新功能时再补:

- 大屏模块集成(用大屏渲染流程图)
- 流程性能调优(实例表分表、缓存策略)
- 路由决策的前端展示页(时间线集成 `wf_ai_route_record`,当前查库/日志)

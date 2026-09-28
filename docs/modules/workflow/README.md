# 工作流模块文档

> 适用模块: `forge-module-workflow`
> 基于引擎: FlowLong 7.0.1

---

## 文档清单(按阅读顺序)

| 序号 | 文档 | 目标读者 | 用途 |
|---|---|---|---|
| [01.workflow-usage.md](01.workflow-usage.md) | 使用说明 | 流程管理员 / 配置人员 / 最终用户 | 菜单导航、完整配置流程、候选人策略、触发器使用、待办/已办、状态码 |
| [02.condition-node-guide.md](02.condition-node-guide.md) | 条件节点指南 | 流程设计者 / 后端开发 | type=3 / type=4 实际行为差异、触发条件使用时机、兜底机制、SpEL 求值 |
| [03.trigger-bean-guide.md](03.trigger-bean-guide.md) | 触发器 Bean 开发指南 | 后端开发 | 自定义 @FlowLongTrigger Bean、AbstractFlowLongTrigger 抽象基类、4 种业务模式、反射调用 |

## 推荐阅读路径

```
新接手流程模块?
  → 01.workflow-usage.md (§1-5 概览,§6 触发器任务)

要配置条件路由?
  → 02.condition-node-guide.md (§2 type=3/4 行为差异,§3-4 处理流程)

要扩展触发器能力(写自定义 Bean)?
  → 03.trigger-bean-guide.md (§4 bean 模式,§7 抽象基类,§8 返回值约定)

排错?
  → 02.condition-node-guide.md §9 排查速查
  → 03.trigger-bean-guide.md §10 调试与日志
```

## 文档约定

- 所有文档之间的相对引用用 `[名称](序号.文件名.md)` 形式,例如 `[02.condition-node-guide.md §2.2](02.condition-node-guide.md#22-作为独立节点--条件不生效)`
- 编号用于**目录排序**和**明确优先级**,新文档按主题选下一个可用编号

## 暂未覆盖

以下内容暂未形成独立文档,需要查代码或新功能时再补:

- 大屏模块集成(用大屏渲染流程图)
- 流程性能调优(实例表分表、缓存策略)
- 高级 AI 审批工作流设计模式

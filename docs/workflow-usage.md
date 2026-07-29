# 审批流程模块使用说明

> 适用版本: forge-admin (基于 FlowLong 7.0.1)
> 适用对象: 流程管理员、审批配置人员、最终用户

---

## 1. 模块概览

forge-admin 工作流模块基于国产开源引擎 **FlowLong**,提供完整的流程设计、部署、发起、审批、跟踪能力。

核心能力:

- **可视化建模**: 浏览器内拖拽式画流程图(开始/审批/并行/结束节点)
- **灵活审批人**: 指定用户、角色、部门、岗位、发起人、连续多级部门负责人、表达式等 11 种策略
- **表单集成**: 自定义表单与业务表单两种类型
- **AI 智能审批**: 高置信度任务自动审批,辅助决策
- **完整追溯**: 实例轨迹、审批意见、任务历史、抄送

---

## 2. 菜单与权限

进入「流程管理」目录后,默认按以下顺序展示子菜单:

| 序号 | 菜单 | 路由 | 用途 | 推荐使用顺序 |
|---|---|---|---|---|
| 1 | 模型管理 | `/workflow/model` | 流程图设计 | 先用,核心入口 |
| 2 | 审批流程 | `/workflow/process` | 已部署流程定义 | 模型部署后产生 |
| 3 | 表单管理 | `/workflow/form` | 自定义表单 | 设计流程前准备 |
| 4 | 流程分类 | `/workflow/category` | 流程分类维护 | 配置流程时引用 |
| 5 | 流程实例 | `/workflow/instance` | 跟踪所有实例 | 运维使用 |
| 6 | 待办任务 | `/workflow/task/todo` | 我的待办 | 普通用户常用 |
| 7 | 已办任务 | `/workflow/task/done` | 我处理过的 | 普通用户常用 |
| 8 | 抄送列表 | `/workflow/copy` | 抄送给我的 | 普通用户 |
| 9 | 表达式管理 | `/workflow/expression` | 候选人表达式 | 高级配置 |
| 10 | 监听器管理 | `/workflow/listener` | 流程级监听器(占位,见 §10) | 高级配置 |

> 触发器节点(独立节点,4 种业务模式)是在「模型管理」中拖出,不在监听器管理中。详见 §6。

> 菜单权限通过 `workflow:*` 前缀控制,详见第 11 节。

---

## 3. 核心概念

理解下面四个概念的层级关系,有助于掌握整个模块:

```
模型 (wf_model / flw_process)           画出来的流程图,未发布
   │  部署
   ▼
流程定义 (wf_process_ext / flw_process) 可发起的流程,版本化
   │  发起
   ▼
流程实例 (flw_instance / flw_his_instance) 一次具体的审批
   │  流转
   ▼
任务 (flw_task / flw_his_task)          每个审批节点上具体要处理的事
```

- **模型**: 在「模型管理」中画图、保存,但**还不能发起**。
- **流程定义**: 模型部署后产生,标记为「激活」状态后可以发起。
- **流程实例**: 用户点「发起」后生成一个实例,沿流程图流转。
- **任务**: 每个审批节点上生成一个待办,分配给候选人处理。

---

## 4. 完整配置流程(管理员视角)

从零开始接入一个审批业务的标准步骤:

### 4.1 创建流程分类(可选但建议)

路径: **流程管理 → 流程分类**

| 字段 | 必填 | 说明 |
|---|---|---|
| 分类名称 | ✓ | 显示名,如「人事类」「财务类」 |
| 分类编码 | ✓ | 唯一标识,模型/流程引用此编码 |
| 父分类 |  | 支持树形结构 |
| 状态 | ✓ | 启用/禁用 |

> **提示**: 分类编码是模型/流程的关联字段,**创建后不能修改**。

### 4.2 创建/编辑表单(可选)

路径: **流程管理 → 表单管理**

支持两种类型:

- **流程表单**: 表单与流程绑定,仅在该流程中可用
- **业务表单**: 业务系统提供的 URL,审批时跳转业务页面

点击「设计」按钮进入表单设计器,拖拽字段、配置校验规则。表单 JSON 会保存到 `wf_form.conf` / `wf_form.fields`。

### 4.3 设计流程模型

路径: **流程管理 → 模型管理**

点击「设计」按钮,进入 FlowLong 可视化设计器:

- 左侧节点面板:**开始节点 / 审批节点 / 并行网关 / 排他网关 / 结束节点 / 子流程**
- 画布: 拖拽节点,拖动连接线设置流转方向
- 右侧属性面板: 选中节点后配置

**审批节点关键配置**:

| 配置项 | 说明 |
|---|---|
| 节点名称 | 显示在审批人待办列表中的标题 |
| 节点标识 | 唯一 key,程序代码中通过它取变量 |
| 候选人策略 | 见第 5 节,决定谁能审批 |
| 表单权限 | 字段级只读/编辑/隐藏 |
| 触发器 | 见第 6 节 |
| 超时提醒 | `remindAuto=true` + `remindAdvanceMinutes=N` |

保存后会生成 `flw_process.model_content` JSON,记录在 `wf_model` 表。

### 4.4 部署流程

模型设计完成后,点击工具栏的「部署」按钮(或在「模型管理」列表中点「部署」):

- 系统会创建一条 `flw_process` 记录,生成 `processId`
- 在 `wf_process_ext` 中记录流程扩展信息
- 部署后流程进入「激活」状态,出现在「审批流程」列表

> **多次部署**: 同一模型可以多次部署,FlowLong 自动按版本号管理,旧版本标记为「历史版本」。

### 4.5 发起流程

路径: **流程管理 → 流程实例 → 发起** 或在「审批流程」列表中点「发起」

- 选择要发起的流程
- 填写业务表单(自定义表单会内嵌,业务表单会跳转)
- 提交后生成 `flw_instance` 活动实例,沿流程图自动流转到第一个审批节点

---

## 5. 候选人策略

审批节点上设置「谁能审批」时,有以下策略可选:

| 策略 | 编码 | 适用场景 | 参数说明 |
|---|---|---|---|
| 指定角色 | 10 | 同一类角色的人都能审 | 角色 ID |
| 部门成员 | 20 | 整个部门的人都能审 | 部门 ID |
| 部门负责人 | 21 | 部门 leader 审批 | 部门 ID |
| 指定岗位 | 22 | 同一岗位都能审 | 岗位 ID |
| 指定用户 | 30 | 直接指定若干人 | 用户 ID 列表 |
| 审批人自选 | 34 | 当前审批人临时选下一个审批人 | 无 |
| 发起人自选 | 35 | 发起时让发起人选 | 无 |
| 发起人自己 | 36 | 让发起人自己审批(常见于复核) | 无 |
| 发起人部门负责人 | 37 | 发起人所在部门的 leader | 无 |
| 连续多级部门负责人 | 38 | 一级 → 二级 → 三级 leader 链式审批 | 级数 |
| 表达式 | 60 | 用 SpEL 表达式动态计算 | 表达式 ID(见下) |

### 5.1 表达式策略详解

候选人为「表达式」时,审批人通过 `wf_process_expression` 表的 ID 引用,运行时 `ExpressionCandidateStrategy` 会查表并计算。

支持的内置变量:

```
${initiator}              流程发起人
${startUser}              同上(别名)
${startUserDeptLeader}    发起人部门负责人(查 sys_dept.leader)
${form_xxx}               表单字段 xxx 的值(取的是用户 ID)
${流程变量名}             直接读流程变量,支持单值/逗号分隔/集合
```

**使用步骤**:

1. 「流程管理 → 表达式管理」新增一条表达式,`expression` 字段填以上语法
2. 设计流程时,节点的 `candidateStrategy=60`,`candidateParam` 填这条表达式的 **ID**(数字)
3. 运行时引擎自动查表并计算候选人

---

## 6. 触发器任务(独立节点)

触发器节点(type=7)是**独立的流程节点**,在 FlowLong 模型中作为一个节点存在。引擎到达该节点时,根据 `extendConfig.triggerType` 字段,自动执行一段逻辑后**直接跳到 childNode**,不需要人工审批。

### 6.1 4 种业务模式

| 模式 | 配置字段 | 适用场景 | 推荐度 |
|---|---|---|---|
| `expression` | `triggerExpression` SpEL | 简单条件判断 / 写流程变量 | ★★★ 80% 场景 |
| `bean` | `triggerBean` + `triggerMethod` | 复杂业务逻辑(调 Spring Bean) | ★★★★★ 90% 复杂场景 |
| `class` | `triggerClass` FQCN + `triggerMethod` | 第三方 jar 工具类 | ★★ 特殊场景 |
| `delegateExpression` | `triggerExpression` SpEL(求 Bean 名) + `triggerMethod` | 按 ctx 动态选 Bean | ★★★ 多租户/多业务线 |

### 6.2 模式选择速查

```
需要"写变量"或"简单条件判断"?
  ├─ 是 → expression 模式
  └─ 否 → 复杂业务逻辑?
            ├─ Spring 容器里的 Bean → bean 模式
            ├─ 第三方 jar / 工具类 → class 模式
            └─ 按 ctx 动态选 → delegateExpression 模式
```

### 6.3 expression 模式 — SpEL 表达式

**extendConfig 配置**:
```json
{
  "triggerType": "expression",
  "triggerExpression": "${amount > 1000000 ? T(SpelUtil).setVar(execution, 'riskLevel', 'A') : false}"
}
```

**设计器配置步骤**:
1. 在触发器节点抽屉,业务模式选 `expression - SpEL 表达式`
2. 填入 SpEL 文本

**常用 SpEL 例子**:

```spel
# 简单条件
${amount > 1000}

# 写流程变量
${T(SpelUtil).setVar(execution, 'riskLevel', amount >= 1000000 ? 'A' : 'B')}

# 调 Spring Bean
${@auditService.logEvent('contract_signed', execution.getFlwInstance().getId())}
```

### 6.4 bean 模式 — 调 Spring Bean(推荐)

**extendConfig 配置**:
```json
{
  "triggerType": "bean",
  "triggerBean": "contractRiskTrigger",
  "triggerMethod": "execute"
}
```

**前置条件**:
- Bean 必须带 `@Component` 和 `@FlowLongTrigger` 注解
- 推荐继承 `AbstractFlowLongTrigger` 抽象基类

**设计器配置步骤**:
1. 业务模式选 `bean - Spring Bean(白名单)`
2. 从下拉选 Bean(后端 `GET /workflow/trigger/beans` 拉取)
3. 填方法名(默认 `execute`)

**示例 Bean**:

```java
@Component
@FlowLongTrigger(name = "合同风险评估", description = "根据金额分级")
public class ContractRiskTrigger extends AbstractFlowLongTrigger {
    @Override
    public boolean execute(Execution execution, NodeModel nodeModel, Map<String, Object> ctx) {
        Long amount = requireArgAs(ctx, "amount", Long.class);
        String riskLevel = amount >= 1_000_000 ? "A" : amount >= 100_000 ? "B" : "C";
        setVar(execution, "riskLevel", riskLevel);
        return true;
    }
}
```

**完整开发指南**: [trigger-bean-guide.md](trigger-bean-guide.md)

### 6.5 class 模式 — 反射 Java 类

**extendConfig 配置**:
```json
{
  "triggerType": "class",
  "triggerClass": "com.example.LegacyAuditor",
  "triggerMethod": "execute"
}
```

完全脱离 Spring 容器,`@Autowired` 字段为 null。生产建议加包前缀白名单。

### 6.6 delegateExpression 模式 — SpEL 求 Bean 名

**extendConfig 配置**:
```json
{
  "triggerType": "delegateExpression",
  "triggerExpression": "industry == 'tech' ? 'techContractRiskTrigger' : 'financeContractRiskTrigger'",
  "triggerMethod": "execute"
}
```

SpEL 求值结果必须是带 `@FlowLongTrigger` 注解的 Bean 名。典型场景:多租户按 tenantId 选 Bean。

### 6.7 失败行为

- `return true` → 流程正常流转到 childNode
- `return false` 或 `throw Exception` → 流程**不流转**,异常向上冒泡
- 节点配置 `continueOnError=true` → 忽略失败,继续流转

详见 [trigger-bean-guide.md §8](trigger-bean-guide.md#8-返回值约定与异常处理)。

### 6.8 适用场景

- **业务数据预处理**: 提交后自动写库(写风险等级、计算金额)
- **流程分支前置判断**: 根据表单数据设置流程变量,供下游条件分支读
- **自动通知/抄送**: 关键节点自动给干系人发消息
- **业务归档**: 流程结束后自动归档历史数据
- **跨系统数据同步**: 流程走到某步时自动调外部 API

> **不要**把复杂业务(调 API、写业务表)全堆在触发器节点,触发器最适合"轻量级旁路判断"。

---

## 7. 待办与已办

### 7.1 待办任务

路径: **流程管理 → 待办任务**

显示当前用户作为候选人的所有待办,操作:

| 操作 | 说明 | 触发 |
|---|---|---|
| 审批 | 同意流转到下一节点 | 任务完成 |
| 驳回 | 回到发起人或上一节点 | 任务驳回 |
| 委派 | 临时让其他人处理,处理完回到你 | 委派 |
| 转办 | 永久转给其他人,你不再参与 | 转办 |
| 认领 | 多个候选人时抢办 | 认领 |
| 退回 | 退回到之前任一节点 | 退回 |
| 撤回 | 发起人在审批人操作前撤回整个流程 | 撤回 |

### 7.2 已办任务

路径: **流程管理 → 已办任务**

显示我处理过的所有任务,可查看处理时间、意见、附件。

### 7.3 抄送列表

路径: **流程管理 → 抄送列表**

抄送不会生成待办,仅做知会通知。

---

## 8. 流程实例查询与跟踪

路径: **流程管理 → 流程实例**

可按流程名称、发起人、状态查询所有实例。点击「详情」进入实例详情,展示:

- 流转轨迹(每个节点的处理人/时间/意见)
- 当前节点位置
- 流程变量(业务表单提交的内容)
- 审批历史(完整时间线)

实例状态说明:

| 状态 | 含义 |
|---|---|
| 审批中 | 正在流转 |
| 审批通过 | 全部节点完成 |
| 审批拒绝 | 有人驳回 |
| 撤销审批 | 发起人撤回 |
| 超时结束 | 配置了超时且到期 |
| 自动通过 / 自动拒绝 | AI 智能审批结果 |

---

## 9. AI 智能审批(可选)

部分场景下可启用 AI 辅助审批,在 `wf_ai_approval_record` 表中记录决策。

启用方式: 在「模型管理」设计节点时,打开「AI 审批」开关,设置:

- **AI 模型**: deepseek / qwen / glm / ernie(系统已集成)
- **置信度阈值**: AI 置信度低于此值时改为人工审批
- **提示词模板**: 告诉 AI 该任务的审批要点

记录可在「流程实例详情 → AI 审批记录」中查询。

---

## 10. 监听器管理(高级)

路径: **流程管理 → 监听器管理**

> ⚠️ **当前状态**: 此模块是 **配置展示入口**,FlowLong 引擎当前不会读取此表。实际运行中的监听器由项目内的 `@Component` Bean(`taskListener` / `instanceListener` 等)提供。**在此页面增删改数据不会影响流程执行行为**。

计划中此表将支持:

- `delegateExpression`: 引用 Spring Bean
- `class`: 全限定类名,引擎反射实例化
- `expression`: SpEL 触发器

未来桥接工作完成后,本节说明会更新。

---

## 11. 权限码参考

`workflow:` 前缀下所有权限码:

| 权限码 | 适用菜单 |
|---|---|
| `workflow:model:list / query / add / edit / delete / deploy` | 模型管理 |
| `workflow:form:list / query / add / edit / delete` | 表单管理 |
| `workflow:category:list / query / add / edit / delete` | 流程分类 |
| `workflow:process:list / query / add / edit / deploy / delete` | 审批流程 |
| `workflow:instance:list / query / start / cancel` | 流程实例 |
| `workflow:task:list / query / claim / complete / delegate / transfer / return` | 任务 |
| `workflow:expression:list / query / add / edit / delete` | 表达式管理 |
| `workflow:listener:list / query / add / edit / delete` | 监听器管理 |

权限分配: **系统管理 → 角色管理 → 分配菜单权限**。

---

## 12. 常见问题

### Q1: 模型已部署但「审批流程」列表找不到?

A: 检查「状态」筛选是否只勾选「激活」。已部署但未激活的流程只在「模型管理」中可见。

### Q2: 候选人策略选了「部门负责人」但任务没人接?

A: 检查 `sys_dept.leader` 字段是否填写且对应用户名在 `sys_user` 中存在。

### Q3: 表达式策略怎么写?

A: 在「表达式管理」中新增,表达式内容用 `${initiator}` / `${form_xxx}` 等语法,然后节点的 `candidateParam` 填这条表达式的 ID(**不是名称**)。

### Q4: 表单字段值怎么传到候选人?

A: 用 `${form_xxx}`,其中 `xxx` 是表单字段名,值必须是用户 ID(或用户名,系统会自动转换)。

### Q5: 如何让流程超时自动结束?

A: 节点 `extendConfig` 中设置 `remindAuto=true`、`remindAdvanceMinutes=N`,系统会在 N 分钟前发送提醒;若配合 `wf_process_listener` 中表达为 `expression` 的实例级触发器,可在超时后自动完成。

### Q6: 流程发起后能改流程图吗?

A: 已经在流转的实例**不会受新版本影响**;同一模型可重新部署,新版本只对**新发起的实例**生效。

### Q7: 怎么撤回已发起的流程?

A: 在「待办任务」或「流程实例」详情中,如果是发起人,操作栏有「撤回」按钮(仅在第一个审批人未操作前有效)。

### Q8: 监听器管理里改了什么,流程有变化吗?

A: **当前没有**。此模块是配置展示入口,运行时由 `@Component` Bean 接管。详见第 10 节。

---

## 13. 附录:状态码

### 流程实例状态(`wf_instance_state`)

```
-2  暂停
-1  暂存待审
 0  审批中
 1  审批通过
 2  审批拒绝
 3  撤销审批
 4  超时结束
 5  强制终止
 6  自动通过
 7  自动拒绝
```

### 审批操作类型(`wf_action_type`)

```
submit / approve / reject / delegate / transfer
return / withdraw / copy / claim / cancel
sign_create / sign_delete
```

### 流程定义状态(`flw_process.process_state`)

```
0  不可用
1  可用(可发起)
2  历史版本
```

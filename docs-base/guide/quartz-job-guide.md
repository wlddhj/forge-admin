# 定时任务（Quartz）使用指南

## 概述

forge-admin 定时任务基于 Quartz 实现，代码集中在 `forge-module-system-biz` 的 `quartz` 包。核心设计：**只有一个 Quartz Job 类**（`QuartzJobExecution`），所有业务任务都是普通 Spring Bean，通过 `sys_job.invoke_target`（格式 `beanName.method(params)`）反射调用。

```
sys_job 表（唯一事实来源）
        │  JobInitRunner（启动时恢复 status=1 的任务）
        ▼
Scheduler（RAMJobStore 内存存储）
        │  CronTrigger（misfire 放弃策略）
        ▼
QuartzJobExecution（唯一 Job 类：超时控制 + 重试 + 写日志 + 统计 + 通知）
        │  applicationContext.getBean(beanName) 反射调用
        ▼
业务任务 Bean（如 DemoTask）
```

## 核心组件

| 组件 | 路径（`forge-module-system-biz/src/main/java/com/forge/modules/system/` 下） | 职责 |
|------|------|------|
| `SysJobController` | `controller/admin/SysJobController.java` | 任务管理接口 `/admin-api/system/job/**` |
| `SysJobLogController` | `controller/admin/SysJobLogController.java` | 日志接口 `/admin-api/system/job-log/**` |
| `SysJobServiceImpl` | `service/impl/SysJobServiceImpl.java` | CRUD + cron 校验 + 调度联动 |
| `QuartzConfig` | `quartz/config/QuartzConfig.java` | SchedulerFactoryBean（线程池 10、RAMJobStore、启动延迟 5s） |
| `JobInitRunner` | `quartz/config/JobInitRunner.java` | ApplicationRunner：启动时把 `status=1` 的任务注册进调度器 |
| `QuartzJobExecution` | `quartz/job/QuartzJobExecution.java` | 唯一 Job 类：执行/超时/重试/日志/统计/通知 |
| `ScheduleService` | `quartz/service/ScheduleService.java` | Scheduler 操作封装（注册/暂停/恢复/删除/立即执行） |
| `JobLogService` | `quartz/service/impl/JobLogServiceImpl.java` | 执行日志写入与查询 |
| `JobNotifyService` | `quartz/service/JobNotifyService.java` | 失败/成功通知（Webhook + 邮件占位） |
| `DemoTask` | `quartz/task/DemoTask.java` | 示例任务 Bean |

实体 `SysJob` / `SysJobLog` 分别在 api 模块 `entity/` 与 biz 模块 `quartz/entity/`。

## 快速上手：新增一个定时任务

### 第 1 步：编写任务 Bean

在 `quartz/task/`（或任意被 Spring 扫描的包）写一个普通 Bean，**不需要实现任何接口或继承任何类**：

```java
@Slf4j
@Component("reportTask")   // bean 名 = invoke_target 前缀
public class ReportTask {

    // 无参方法
    public void refreshDaily() {
        log.info("日报刷新执行");
    }

    // 带参方法：字符串参数需在 invoke_target 中带引号
    public void execute(String message) {
        log.info("任务消息: {}", message);
    }
}
```

### 第 2 步：配置任务记录

在「基础设施 → 定时任务」页面新增，或直接插入 `sys_job`：

| 字段 | 说明 | 示例 |
|------|------|------|
| `job_name` | 任务名称 | 日报刷新 |
| `job_group` | 分组（字典 `sys_job_group`） | DEFAULT / SYSTEM |
| `invoke_target` | 调用目标 | `reportTask.refreshDaily()` |
| `cron_expression` | Quartz cron（6/7 位，支持 `?`） | `0 0 2 * * ?` |
| `status` | 0 暂停 / 1 正常 | 1 |
| `timeout` | 超时秒数（null 不限） | 60 |
| `retry_count` / `retry_interval` | 失败重试次数 / 间隔秒 | 2 / 60 |
| `job_params` | JSON 对象，优先于 inline 参数 | `{"type":"daily"}` |

保存时后端会先校验 `CronExpression.isValidExpression()`，cron 非法直接拒绝。

### 第 3 步：验证

- 列表页「执行一次」按钮立即触发（写操作日志，businessType=OTHER）
- 观察后端日志输出与「任务日志」页面记录
- 「下次执行时间」列由后端 `CronExpression.parse(cron).next(now)` 计算

## 调用目标（invoke_target）详解

格式：`beanName.methodName` 或 `beanName.methodName(参数列表)`。

**参数匹配规则**（`QuartzJobExecution.executeInvokeTarget`）：

1. `job_params`（JSON 字段）非空且目标方法存在**仅 1 个 Map 参数**的重载 → 优先以 Map 方式调用
2. 否则解析括号内 inline 参数，**按参数个数匹配方法**（不校验类型名），类型自动推断：

| 写法 | 推断类型 |
|------|---------|
| `"abc"` / `'abc'` | String |
| `true` / `false` | Boolean |
| `123` | Long |
| `1.5` | Double |
| `null` | null |
| 其它解析失败 | String |

示例：`demoTask.multiParams("a", 1, true)` 匹配 `multiParams(String, Integer, Boolean)`（按个数 3 匹配）。

> ⚠️ **bean 不存在不会在保存时报错**——`getBean(beanName)` 在触发时才执行，错误只会出现在任务日志中。新增任务后请「执行一次」验证。

## 调度行为说明

### 存储与恢复

- **RAMJobStore（内存存储）**：Quartz 内存态不持久化，重启即清空；`sys_job` 表是唯一事实来源，每次启动由 `JobInitRunner` 重新注册 `status=1` 的任务（单任务注册失败仅记 error 日志，不中断其它任务）
- 修改任务 = 先 pause → 更新记录 → 若 `status=1` 重新 scheduleJob（删除重建 JobDetail）
- 删除任务时只清理 `status=1` 任务的调度器残留；暂停态任务无 Quartz 残留（重启自然清空）

### Misfire 策略

`withMisfireHandlingInstructionDoNothing()`：错过的触发**直接放弃，不补跑**（应用停机期间错过的任务在重启后不会补执行）。

### 并发控制（重要）

`QuartzJobExecution` 类级标注 `@DisallowConcurrentExecution`，**所有任务实际都禁止并发**——上一次触发未结束时，同一任务的下一次触发会等待。

> ⚠️ 页面上的「是否并发执行」（`sys_job.concurrent`）字段**当前未接入调度行为**，仅作记录展示。

### 暂停 / 恢复 / 立即执行

| 操作 | 行为 |
|------|------|
| 暂停（status=0） | `pauseJob` + `pauseTrigger`，同时更新表状态 |
| 恢复（status=1） | `resumeJob` + `resumeTrigger`；若任务不存在（如重启后未注册）自动重建 |
| 执行一次 | `scheduler.triggerJob(jobKey)`，不影响原有 cron 节奏 |

## 执行链：超时 / 重试 / 统计 / 通知

`QuartzJobExecution.execute` 单次触发的完整流程：

1. **执行**：单线程池 `Future.get(timeout, SECONDS)` 控制超时（`timeout` 为 null 则不限制）
2. **重试**：失败后最多重试 `retry_count` 次，间隔 `retry_interval` 秒（`Thread.sleep` 阻塞 Quartz 工作线程）
3. **写日志**（`@Async` 异步）：写入 `sys_job_log`，超时错误（message 含 "Timeout"）状态记为 `TIMEOUT`
4. **更新统计**：`last_execute_at / last_execute_status / last_execute_duration / total_execute_count / success_count / failure_count`
5. **通知**：按 `notify_config` JSON 决定——
   - `notifyOnFailure`（默认 true）/ `notifyOnSuccess`（默认 false）
   - `webhookUrl`：HTTP POST JSON（5 秒超时）
   - `emails`：**当前为日志占位，未实际发信**

```json
// notify_config 示例
{ "notifyOnFailure": true, "notifyOnSuccess": false, "webhookUrl": "http://your-hook/api", "emails": ["ops@example.com"] }
```

## 任务日志

`sys_job_log` 字段：`job_id / job_name / job_group / invoke_target / job_message / status（1 成功 0 失败）/ exception_info / start_time / end_time / duration（毫秒）/ create_time`。

- 页面：基础设施 → 任务日志（路由 `/system/job-log`），支持按任务过滤、查看异常详情、清空；耗时 >5000ms 标红
- 从任务列表「日志」按钮跳转时自动按 jobId 预过滤

## 管理界面操作

前端页面 `apps/forge-web/src/views/system/job/`：

- **状态切换**：列表内 `el-switch` 直接启停
- **Cron 生成器**：编辑对话框点「生成」弹出常用预设（每 5/10/30 分钟、每小时、每天 0 点/凌晨 2 点、每周一、每月 1 号）
- **执行统计**：列表展示最后执行时间、状态标签（SUCCESS/FAIL/TIMEOUT）、成功/失败计数
- 桌面/移动端双布局

## 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/admin-api/system/job/list` | `system:job:list` | 分页查询 |
| GET | `/admin-api/system/job/{id}` | `system:job:query` | 详情 |
| POST | `/admin-api/system/job` | `system:job:add` | 新增 |
| PUT | `/admin-api/system/job` | `system:job:edit` | 修改 |
| DELETE | `/admin-api/system/job/{ids}` | `system:job:delete` | 删除 |
| PUT | `/admin-api/system/job/{id}/status?status=` | `system:job:edit` | 启停 |
| POST | `/admin-api/system/job/{id}/run` | `system:job:edit` | 执行一次 |
| GET | `/admin-api/system/job-log/list` | `system:job:query` | 日志分页 |
| DELETE | `/admin-api/system/job-log/{ids}` | `system:job:delete` | 删除日志 |
| DELETE | `/admin-api/system/job-log/clear?jobId=` | `system:job:delete` | 清空日志 |

写操作均带 `@OperationLog` 审计（新增/修改/删除/启停/执行一次/日志清理）。

> ⚠️ 初始菜单 SQL 中「任务日志」页面权限为 `monitor:job-log:list`，与后端实际使用的 `system:job:query` 不一致；如遇日志页无数据，请以接口权限码为准调整菜单/角色配置。

## 多租户注意事项

- `sys_job` 在 `forge.tenant.ignore-tables` 中：**跨租户共享**，所有租户共用任务定义（`sys_job` 实体无 tenantId）
- `sys_job_log` 建有 `tenant_id` 列，但 `SysJobLog` 实体未映射该字段；Quartz 工作线程**没有租户上下文**（`TenantJobAspect` 只拦截 `@Scheduled` 注解方法，对 Quartz 触发的反射调用无效），租户开关开启时任务内操作租户数据需自行处理上下文（参考 `TenantContextHolder`，基于 TransmittableThreadLocal）

## 已知限制与 FAQ

| 问题 | 说明 |
|------|------|
| 重启会补跑错过的任务吗 | 不会。misfire 策略为 DoNothing，错过的触发直接放弃 |
| 任务 Bean 改代码后生效吗 | Bean 由 Spring 管理，重启应用即生效；任务注册每次启动由 `JobInitRunner` 重建 |
| 为什么保存任务时不校验 bean 是否存在 | 设计如此，`getBean` 在触发时执行；请保存后「执行一次」验证 |
| 重试会阻塞其它任务吗 | 重试间隔用 `Thread.sleep` 占用 Quartz 工作线程（线程池 10），长间隔高频任务需注意线程占用 |
| inline 参数支持哪些类型 | String/Boolean/Long/Double/null（见上文推断表）；复杂参数用 `job_params` JSON + Map 形参方法 |
| 邮件通知发不出 | `emails` 当前为占位实现，仅 Webhook 可用 |

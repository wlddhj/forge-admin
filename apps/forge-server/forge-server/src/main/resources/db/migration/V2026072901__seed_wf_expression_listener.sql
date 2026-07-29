-- ========================================
-- 流程表达式管理种子数据
-- ========================================
-- 表已由 V2026052701__wf_expression_listener.sql 创建,此处仅补齐常用表达式
-- 表达式用途: ExpressionCandidateStrategy(候选人策略) / TaskTriggerHandler(任务触发器)
-- 支持的内置变量: ${initiator} ${startUser} ${startUserDeptLeader}
-- 支持的表单字段: ${form_fieldName}
-- 流程变量: 直接变量名

INSERT INTO `wf_process_expression` (`name`, `status`, `expression`, `remark`) VALUES
('流程发起人',            1, '${initiator}',            '解析为流程发起人,等同 ${startUser},候选人策略可用'),
('发起人本人',            1, '${startUser}',            '任务回退给发起人时使用'),
('发起人部门负责人',      1, '${startUserDeptLeader}',  '从 sys_dept.leader 解析出部门负责人用户名再查 sys_user'),
('表单字段-申请人',       1, '${form_applicantId}',     '从表单字段 applicantId 读取用户ID'),
('表单字段-项目经理',     1, '${form_projectManager}',  '从表单字段 projectManager 读取用户名'),
('表单字段-费用承担人',   1, '${form_costOwner}',       '用于费用报销流程'),
('流程变量-指定审批人',   1, '${approver}',             '从流程变量 approver 读取,支持单值或逗号分隔'),
('流程变量-会签人员',     1, '${countersignUsers}',     '从流程变量 countersignUsers 读取用户列表'),
('HRBP 角色用户',         1, '${hrbp}',                 '流程变量 hrbp,通常由前置节点设置'),
('申请人直属上级',        1, '${initiatorLeader}',      '扩展场景:由发起人上级审批');

-- ========================================
-- 流程监听器管理种子数据
-- ========================================
-- 框架值类型约定(class/delegateExpression/expression)
-- type 取值: execution(流程级) task(任务级)
-- execution 事件: start / end
-- task 事件: create / assignment / complete / delete

INSERT INTO `wf_process_listener` (`name`, `status`, `type`, `event`, `value_type`, `value`, `remark`) VALUES
-- delegateExpression: 直接引用 Spring 容器中的 Bean
('系统任务复合监听器',         1, 'task',     'create',     'delegateExpression',  'taskListener',     '系统内置 CompositeTaskListener,负责候选人分配+通知+AI审批+提醒时间'),
('系统任务复合监听器-完成',    1, 'task',     'complete',   'delegateExpression',  'taskListener',     '框架按事件类型路由,完成/驳回/转办走同一 Bean'),
('系统任务复合监听器-分配',    1, 'task',     'assignment', 'delegateExpression',  'taskListener',     '转办/委派时复用同一监听器'),
('系统任务复合监听器-删除',    1, 'task',     'delete',     'delegateExpression',  'taskListener',     '任务撤回或作废时复用同一监听器'),
('系统实例事件监听器',         1, 'execution','start',      'delegateExpression',  'instanceListener', '系统内置 InstanceEventListener,发布 WorkflowInstanceEvent 供业务订阅'),
('系统实例事件监听器-结束',    1, 'execution','end',        'delegateExpression',  'instanceListener', '流程结束归档时复用同一监听器'),

-- class: 通过全限定类名实例化(需实现 FlowLong 对应 Listener 接口)
('流程发起日志-审计',          1, 'execution','start',      'class',               'com.forge.modules.workflow.handler.event.AuditLogEventHandler', '审计日志:记录发起人/时间/业务Key'),
('流程结束日志-审计',          1, 'execution','end',        'class',               'com.forge.modules.workflow.handler.event.AuditLogEventHandler', '审计日志:记录实例结束'),
('任务创建通知',               1, 'task',     'create',     'class',               'com.forge.modules.workflow.listener.TaskNotificationListener', 'WebSocket 推送待办给候选人'),
('任务完成通知',               1, 'task',     'complete',   'class',               'com.forge.modules.workflow.listener.TaskNotificationListener', '完成时通知发起人'),
('任务转办通知',               1, 'task',     'assignment', 'class',               'com.forge.modules.workflow.listener.TaskNotificationListener', '转办/委派时通知新处理人'),
('任务候选人自动分配',         1, 'task',     'create',     'class',               'com.forge.modules.workflow.listener.BpmTaskCandidateListener', '从节点 extendConfig.candidateStrategy 解析策略码并计算候选人'),
('实例事件转发',               1, 'execution','start',      'class',               'com.forge.modules.workflow.listener.InstanceEventListener', '实例级事件总线,与 instanceListener Bean 等价'),

-- expression: 通过 SpEL 表达式计算,需配合 TaskTriggerHandler 使用
('流程发起无条件触发器',       1, 'execution','start',      'expression',          '${true}', 'TaskTriggerHandler 触发器,流程开始时无条件执行'),
('任务创建-高优先级触发',      1, 'task',     'create',     'expression',          '${task.priority > 5}', '仅高优先级任务触发,优先级从流程变量读取'),
('流程结束-有业务Key触发',     1, 'execution','end',        'expression',          '${instance.businessKey != null}', '有业务Key的实例才触发后续处理');
-- ========================================
-- forge-admin 工作流模块初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建时间: 2026-06-02
-- 更新时间: 2026-06-27
-- 说明: 本脚本包含工作流模块相关的表结构和数据
-- 技术栈: FlowLong 7.0.1 (国产工作流引擎)
-- 前置依赖: sql/init.sql (系统基础表)
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `forge_admin`;

-- ========================================
-- Part 1: FlowLong 核心表（8张）
-- 基于 FlowLong 官方 MySQL 脚本
-- ========================================

-- 1. 流程定义表
DROP TABLE IF EXISTS `flw_process`;
CREATE TABLE `flw_process` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `create_id` VARCHAR(50) NOT NULL COMMENT '创建人ID',
    `create_by` VARCHAR(50) NOT NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP NOT NULL COMMENT '创建时间',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程定义 key 唯一标识',
    `process_name` VARCHAR(100) NOT NULL COMMENT '流程定义名称',
    `process_icon` VARCHAR(255) DEFAULT NULL COMMENT '流程图标地址',
    `process_type` VARCHAR(100) COMMENT '流程类型',
    `process_version` INT NOT NULL DEFAULT 1 COMMENT '流程版本，默认 1',
    `instance_url` VARCHAR(200) COMMENT '实例地址',
    `remark` VARCHAR(255) COMMENT '备注说明',
    `use_scope` TINYINT NOT NULL DEFAULT 0 COMMENT '使用范围 0，全员 1，指定人员 2，均不可提交',
    `process_state` TINYINT NOT NULL DEFAULT 1 COMMENT '流程状态 0，不可用 1，可用 2，历史版本',
    `model_content` LONGTEXT COMMENT '流程模型定义JSON内容',
    `sort` TINYINT DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    INDEX `idx_process_name` (`process_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义表';

-- 2. 流程实例表（活动）
DROP TABLE IF EXISTS `flw_instance`;
CREATE TABLE `flw_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `create_id` VARCHAR(50) NOT NULL COMMENT '创建人ID',
    `create_by` VARCHAR(50) NOT NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP NOT NULL COMMENT '创建时间',
    `process_id` BIGINT NOT NULL COMMENT '流程定义ID',
    `parent_instance_id` BIGINT COMMENT '父流程实例ID',
    `priority` TINYINT COMMENT '优先级',
    `instance_no` VARCHAR(50) COMMENT '流程实例编号',
    `business_key` VARCHAR(100) COMMENT '业务KEY',
    `variable` LONGTEXT COMMENT '变量json',
    `current_node_name` VARCHAR(100) NOT NULL COMMENT '当前所在节点名称',
    `current_node_key` VARCHAR(100) NOT NULL COMMENT '当前所在节点key',
    `expire_time` TIMESTAMP NULL DEFAULT NULL COMMENT '期望完成时间',
    `last_update_by` VARCHAR(50) COMMENT '上次更新人',
    `last_update_time` TIMESTAMP NULL DEFAULT NULL COMMENT '上次更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_instance_process_id` (`process_id`),
    CONSTRAINT `fk_instance_process_id` FOREIGN KEY (`process_id`) REFERENCES `flw_process` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例表';

-- 3. 历史流程实例表
DROP TABLE IF EXISTS `flw_his_instance`;
CREATE TABLE `flw_his_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `create_id` VARCHAR(50) NOT NULL COMMENT '创建人ID',
    `create_by` VARCHAR(50) NOT NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP NOT NULL COMMENT '创建时间',
    `process_id` BIGINT NOT NULL COMMENT '流程定义ID',
    `parent_instance_id` BIGINT COMMENT '父流程实例ID',
    `priority` TINYINT COMMENT '优先级',
    `instance_no` VARCHAR(50) COMMENT '流程实例编号',
    `business_key` VARCHAR(100) COMMENT '业务KEY',
    `variable` LONGTEXT COMMENT '变量json',
    `current_node_name` VARCHAR(100) NOT NULL COMMENT '当前所在节点名称',
    `current_node_key` VARCHAR(100) NOT NULL COMMENT '当前所在节点key',
    `expire_time` TIMESTAMP NULL DEFAULT NULL COMMENT '期望完成时间',
    `last_update_by` VARCHAR(50) COMMENT '上次更新人',
    `last_update_time` TIMESTAMP NULL DEFAULT NULL COMMENT '上次更新时间',
    `instance_state` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 -2暂停 -1暂存 0审批中 1通过 2拒绝 3撤销 4超时 5终止 6自动通过 7自动拒绝',
    `end_time` TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间',
    `duration` BIGINT COMMENT '处理耗时',
    PRIMARY KEY (`id`),
    INDEX `idx_his_instance_process_id` (`process_id`),
    CONSTRAINT `fk_his_instance_process_id` FOREIGN KEY (`process_id`) REFERENCES `flw_process` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史流程实例表';

-- 4. 任务表（活动）
DROP TABLE IF EXISTS `flw_task`;
CREATE TABLE `flw_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `create_id` VARCHAR(50) NOT NULL COMMENT '创建人ID',
    `create_by` VARCHAR(50) NOT NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP NOT NULL COMMENT '创建时间',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `parent_task_id` BIGINT COMMENT '父任务ID',
    `call_process_id` BIGINT COMMENT '调用外部流程定义ID',
    `call_instance_id` BIGINT COMMENT '调用外部流程实例ID',
    `task_name` VARCHAR(100) NOT NULL COMMENT '任务名称',
    `task_key` VARCHAR(100) NOT NULL COMMENT '任务 key 唯一标识',
    `task_type` TINYINT NOT NULL COMMENT '任务类型',
    `perform_type` TINYINT COMMENT '参与类型',
    `action_url` VARCHAR(200) COMMENT '任务处理的url',
    `variable` LONGTEXT COMMENT '变量json',
    `assignor_id` VARCHAR(100) COMMENT '委托人ID',
    `assignor` VARCHAR(255) COMMENT '委托人',
    `expire_time` TIMESTAMP NULL DEFAULT NULL COMMENT '任务期望完成时间',
    `remind_time` TIMESTAMP NULL DEFAULT NULL COMMENT '提醒时间',
    `remind_repeat` TINYINT NOT NULL DEFAULT 0 COMMENT '提醒次数',
    `viewed` TINYINT NOT NULL DEFAULT 0 COMMENT '已阅 0否 1是',
    PRIMARY KEY (`id`),
    INDEX `idx_task_instance_id` (`instance_id`),
    CONSTRAINT `fk_task_instance_id` FOREIGN KEY (`instance_id`) REFERENCES `flw_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- 5. 历史任务表
DROP TABLE IF EXISTS `flw_his_task`;
CREATE TABLE `flw_his_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `create_id` VARCHAR(50) NOT NULL COMMENT '创建人ID',
    `create_by` VARCHAR(50) NOT NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP NOT NULL COMMENT '创建时间',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `parent_task_id` BIGINT COMMENT '父任务ID',
    `call_process_id` BIGINT COMMENT '调用外部流程定义ID',
    `call_instance_id` BIGINT COMMENT '调用外部流程实例ID',
    `task_name` VARCHAR(100) NOT NULL COMMENT '任务名称',
    `task_key` VARCHAR(100) NOT NULL COMMENT '任务 key 唯一标识',
    `task_type` TINYINT NOT NULL COMMENT '任务类型',
    `perform_type` TINYINT COMMENT '参与类型',
    `action_url` VARCHAR(200) COMMENT '任务处理的url',
    `variable` LONGTEXT COMMENT '变量json',
    `assignor_id` VARCHAR(100) COMMENT '委托人ID',
    `assignor` VARCHAR(255) COMMENT '委托人',
    `expire_time` TIMESTAMP NULL DEFAULT NULL COMMENT '任务期望完成时间',
    `remind_time` TIMESTAMP NULL DEFAULT NULL COMMENT '提醒时间',
    `remind_repeat` TINYINT NOT NULL DEFAULT 0 COMMENT '提醒次数',
    `viewed` TINYINT NOT NULL DEFAULT 0 COMMENT '已阅 0否 1是',
    `finish_time` TIMESTAMP NULL DEFAULT NULL COMMENT '任务完成时间',
    `task_state` TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态',
    `duration` BIGINT COMMENT '处理耗时',
    PRIMARY KEY (`id`),
    INDEX `idx_his_task_instance_id` (`instance_id`),
    INDEX `idx_his_task_parent_task_id` (`parent_task_id`),
    CONSTRAINT `fk_his_task_instance_id` FOREIGN KEY (`instance_id`) REFERENCES `flw_his_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史任务表';

-- 6. 任务参与者表（活动）
DROP TABLE IF EXISTS `flw_task_actor`;
CREATE TABLE `flw_task_actor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `actor_id` VARCHAR(100) NOT NULL COMMENT '参与者ID',
    `actor_name` VARCHAR(100) NOT NULL COMMENT '参与者名称',
    `actor_type` INT NOT NULL COMMENT '参与者类型 0用户 1角色 2部门',
    `weight` INT COMMENT '权重',
    `agent_id` VARCHAR(100) COMMENT '代理人ID',
    `agent_type` INT COMMENT '代理人类型',
    `ext` LONGTEXT COMMENT '扩展json',
    PRIMARY KEY (`id`),
    INDEX `idx_task_actor_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务参与者表';

-- 7. 历史任务参与者表
DROP TABLE IF EXISTS `flw_his_task_actor`;
CREATE TABLE `flw_his_task_actor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `actor_id` VARCHAR(100) NOT NULL COMMENT '参与者ID',
    `actor_name` VARCHAR(100) NOT NULL COMMENT '参与者名称',
    `actor_type` INT NOT NULL COMMENT '参与者类型 0用户 1角色 2部门',
    `weight` INT COMMENT '权重',
    `agent_id` VARCHAR(100) COMMENT '代理人ID',
    `agent_type` INT COMMENT '代理人类型',
    `ext` LONGTEXT COMMENT '扩展json',
    PRIMARY KEY (`id`),
    INDEX `idx_his_task_actor_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史任务参与者表';

-- 8. 扩展流程实例表
DROP TABLE IF EXISTS `flw_ext_instance`;
CREATE TABLE `flw_ext_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(50) COMMENT '租户ID',
    `process_id` BIGINT NOT NULL COMMENT '流程定义ID',
    `process_name` VARCHAR(100) COMMENT '流程名称',
    `process_type` VARCHAR(100) COMMENT '流程类型',
    `model_content` LONGTEXT COMMENT '流程模型定义JSON内容',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_ext_instance_id` FOREIGN KEY (`id`) REFERENCES `flw_his_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扩展流程实例表';

-- ========================================
-- Part 2: 自定义扩展表（7张）
-- ========================================

-- 9. 流程分类表
DROP TABLE IF EXISTS `wf_category`;
CREATE TABLE `wf_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category_name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `category_code` VARCHAR(100) NOT NULL COMMENT '分类编码',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程分类表';

-- 10. 流程定义扩展表（关联 FlowLong 流程定义）
DROP TABLE IF EXISTS `wf_process_ext`;
CREATE TABLE `wf_process_ext` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `process_id` BIGINT NULL COMMENT 'FlowLong流程定义ID（关联flw_process.id，NULL表示草稿状态）',
    `process_key` VARCHAR(64) NOT NULL COMMENT '流程标识',
    `process_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
    `category_id` BIGINT COMMENT '分类ID（关联wf_category.id）',
    `description` TEXT COMMENT '流程描述',
    `form_type` INT COMMENT '表单类型(10流程表单 20业务表单)',
    `form_id` BIGINT COMMENT '关联表单ID',
    `auto_copy_strategy` INT COMMENT '自动抄送策略',
    `auto_copy_param` VARCHAR(512) COMMENT '自动抄送参数',
    `model_json` LONGTEXT COMMENT 'FlowLong流程模型JSON内容',
    `meta_info` TEXT COMMENT '元信息JSON（存储表单配置等扩展信息）',
    `create_by` BIGINT COMMENT '创建人ID',
    `create_by_name` VARCHAR(100) COMMENT '创建人名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    KEY `idx_process_id` (`process_id`),
    KEY `idx_process_key` (`process_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义扩展表';

-- 11. 审批意见表（补充 FlowLong 缺失的审批文本和附件）
DROP TABLE IF EXISTS `wf_approval_comment`;
CREATE TABLE `wf_approval_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `task_def_key` VARCHAR(200) DEFAULT NULL COMMENT '任务定义Key',
    `task_name` VARCHAR(200) DEFAULT NULL COMMENT '任务名称',
    `user_id` BIGINT NOT NULL COMMENT '审批人ID',
    `user_name` VARCHAR(100) DEFAULT NULL COMMENT '审批人名称',
    `action_type` VARCHAR(30) NOT NULL COMMENT '操作类型(submit/approve/reject/delegate/transfer/return/claim/copy/withdraw/cancel/sign_create/sign_delete)',
    `comment_text` TEXT DEFAULT NULL COMMENT '审批意见',
    `attachment_ids` VARCHAR(500) DEFAULT NULL COMMENT '附件ID列表(逗号分隔)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批意见表';

-- 12. 工作流表单管理表
DROP TABLE IF EXISTS `wf_form`;
CREATE TABLE `wf_form` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(64) NOT NULL COMMENT '表单名称',
    `status` TINYINT DEFAULT 0 COMMENT '状态(0正常 1停用)',
    `conf` TEXT COMMENT '表单配置(JSON)',
    `fields` TEXT COMMENT '表单字段列表(JSON数组)',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流表单';

-- 13. 流程表达式管理
DROP TABLE IF EXISTS `wf_process_expression`;
CREATE TABLE `wf_process_expression` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` VARCHAR(128) NOT NULL COMMENT '表达式名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0:停用 1:启用)',
    `expression` VARCHAR(512) NOT NULL COMMENT '表达式内容',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程表达式';

-- 14. 流程监听器管理
DROP TABLE IF EXISTS `wf_process_listener`;
CREATE TABLE `wf_process_listener` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` VARCHAR(128) NOT NULL COMMENT '监听器名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0:停用 1:启用)',
    `type` VARCHAR(32) NOT NULL COMMENT '监听类型(execution/task)',
    `event` VARCHAR(32) NOT NULL COMMENT '监听事件',
    `value_type` VARCHAR(32) NOT NULL COMMENT '值类型(class/delegateExpression/expression)',
    `value` VARCHAR(512) NOT NULL COMMENT '值',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程监听器';

-- 15. AI审批记录表
DROP TABLE IF EXISTS `wf_ai_approval_record`;
CREATE TABLE `wf_ai_approval_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `task_def_key` VARCHAR(100) DEFAULT NULL COMMENT '任务定义Key',
    `task_name` VARCHAR(200) DEFAULT NULL COMMENT '任务名称',
    `decision` VARCHAR(20) NOT NULL COMMENT 'AI决策结果（APPROVE/REJECT/MANUAL）',
    `confidence` INT DEFAULT NULL COMMENT '置信度（0-100）',
    `reasoning` TEXT DEFAULT NULL COMMENT 'AI分析说明',
    `raw_response` TEXT DEFAULT NULL COMMENT '原始AI响应',
    `status` VARCHAR(20) NOT NULL COMMENT '执行状态（SUCCESS/FAILURE/LOW_CONFIDENCE/TIMEOUT/ERROR）',
    `provider` VARCHAR(50) DEFAULT NULL COMMENT 'AI模型提供商',
    `model_name` VARCHAR(100) DEFAULT NULL COMMENT 'AI模型名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI审批记录表';

-- ========================================
-- Part 2.5: 表达式与监听器种子数据
-- 说明: 表达式用于 ExpressionCandidateStrategy(候选人策略) / TaskTriggerHandler(任务触发器)
--       监听器覆盖 FlowLong 流程级(execution)和任务级(task)事件
-- 表达式语法: ${initiator} / ${startUser} / ${startUserDeptLeader} 内置变量
--             ${form_fieldName} 表单字段引用,直接变量名走流程变量
-- 监听器值类型: class(全限定类名) / delegateExpression(Bean 名) / expression(SpEL)
-- ========================================

-- 表达式种子(10 条)
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

-- 监听器种子(16 条)
-- delegateExpression: 直接引用 Spring 容器中的 Bean
-- class: 通过全限定类名实例化(需实现 FlowLong 对应 Listener 接口)
-- expression: 通过 SpEL 表达式计算,需配合 TaskTriggerHandler 使用
INSERT INTO `wf_process_listener` (`name`, `status`, `type`, `event`, `value_type`, `value`, `remark`) VALUES
-- delegateExpression 引用内置 Bean
('系统任务复合监听器',         1, 'task',     'create',     'delegateExpression',  'taskListener',     '系统内置 CompositeTaskListener,负责候选人分配+通知+AI审批+提醒时间'),
('系统任务复合监听器-完成',    1, 'task',     'complete',   'delegateExpression',  'taskListener',     '框架按事件类型路由,完成/驳回/转办走同一 Bean'),
('系统任务复合监听器-分配',    1, 'task',     'assignment', 'delegateExpression',  'taskListener',     '转办/委派时复用同一监听器'),
('系统任务复合监听器-删除',    1, 'task',     'delete',     'delegateExpression',  'taskListener',     '任务撤回或作废时复用同一监听器'),
('系统实例事件监听器',         1, 'execution','start',      'delegateExpression',  'instanceListener', '系统内置 InstanceEventListener,发布 WorkflowInstanceEvent 供业务订阅'),
('系统实例事件监听器-结束',    1, 'execution','end',        'delegateExpression',  'instanceListener', '流程结束归档时复用同一监听器'),
-- class 引用项目内真实类
('任务创建通知',               1, 'task',     'create',     'class',               'com.forge.modules.workflow.listener.TaskNotificationListener', 'WebSocket 推送待办给候选人'),
('任务完成通知',               1, 'task',     'complete',   'class',               'com.forge.modules.workflow.listener.TaskNotificationListener', '完成时通知发起人'),
('任务转办通知',               1, 'task',     'assignment', 'class',               'com.forge.modules.workflow.listener.TaskNotificationListener', '转办/委派时通知新处理人'),
('任务候选人自动分配',         1, 'task',     'create',     'class',               'com.forge.modules.workflow.listener.BpmTaskCandidateListener', '从节点 extendConfig.candidateStrategy 解析策略码并计算候选人'),
-- expression 触发器示例
('任务创建-高优先级触发',      1, 'task',     'create',     'expression',          '${task.priority > 5}', '仅高优先级任务触发,优先级从流程变量读取'),
('流程结束-有业务Key触发',     1, 'execution','end',        'expression',          '${instance.businessKey != null}', '有业务Key的实例才触发后续处理');

-- ========================================
-- Part 3: 工作流模块菜单
-- ========================================

-- 流程管理目录（顶级目录）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(100, '流程管理', 0, '/workflow', 'Layout', '/workflow/model', 'SetUp', 2, 0, NULL, 1, 1, 0, 0);

-- 模型管理菜单（sort_order=1，最高优先级）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(210, '模型管理', 100, '/workflow/model', '/views/workflow/model/index', NULL, 'Files', 1, 1, 'workflow:model:list', 1, 1, 0, 0);

-- 模型管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(211, '模型查询', 210, '', '', NULL, '', 1, 2, 'workflow:model:query', 1, 1, 0, 0),
(212, '模型新增', 210, '', '', NULL, '', 2, 2, 'workflow:model:add', 1, 1, 0, 0),
(213, '模型编辑', 210, '', '', NULL, '', 3, 2, 'workflow:model:edit', 1, 1, 0, 0),
(214, '模型部署', 210, '', '', NULL, '', 4, 2, 'workflow:model:deploy', 1, 1, 0, 0),
(215, '模型删除', 210, '', '', NULL, '', 5, 2, 'workflow:model:delete', 1, 1, 0, 0),
(216, '模型设计', 210, '/workflow/model/designer', '/views/workflow/model/FlowLongModelDesigner', NULL, '', 10, 1, NULL, 1, 0, 0, 0);

-- 审批流程菜单（sort_order=2）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(101, '审批流程', 100, '/workflow/process', '/views/workflow/process/index', NULL, 'EditPen', 2, 1, 'workflow:process:list', 1, 1, 0, 0);

-- 审批流程按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(102, '流程查询', 101, '', '', NULL, '', 1, 2, 'workflow:process:query', 1, 1, 0, 0),
(103, '流程新增', 101, '', '', NULL, '', 2, 2, 'workflow:process:add', 1, 1, 0, 0),
(104, '流程编辑', 101, '', '', NULL, '', 3, 2, 'workflow:process:edit', 1, 1, 0, 0),
(105, '流程部署', 101, '', '', NULL, '', 4, 2, 'workflow:process:deploy', 1, 1, 0, 0),
(106, '流程删除', 101, '', '', NULL, '', 5, 2, 'workflow:process:delete', 1, 1, 0, 0);

-- 审批流程设计器（hidden）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(124, '审批流程设计器', 101, '/workflow/process/designer', '/views/workflow/process/ProcessDesigner', NULL, '', 10, 1, NULL, 1, 0, 0, 0);

-- 表单管理菜单（sort_order=3）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(200, '表单管理', 100, '/workflow/form', '/views/workflow/form/index', NULL, 'Document', 3, 1, 'workflow:form:list', 1, 1, 0, 0);

-- 表单设计器路由（hidden）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(201, '表单设计器', 200, '/workflow/form/editor', '/views/workflow/form/FormEditor', NULL, '', 1, 1, 'workflow:form:edit', 1, 0, 0, 0);

-- 表单管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(202, '表单查询', 200, '', '', NULL, '', 1, 2, 'workflow:form:query', 1, 1, 0, 0),
(203, '表单新增', 200, '', '', NULL, '', 2, 2, 'workflow:form:add', 1, 1, 0, 0),
(204, '表单编辑', 200, '', '', NULL, '', 3, 2, 'workflow:form:edit', 1, 1, 0, 0),
(205, '表单删除', 200, '', '', NULL, '', 4, 2, 'workflow:form:delete', 1, 1, 0, 0);

-- 流程分类菜单（sort_order=4）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(107, '流程分类', 100, '/workflow/category', '/views/workflow/category/index', NULL, 'FolderOpened', 4, 1, 'workflow:category:list', 1, 1, 0, 0);

-- 流程分类按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(108, '分类查询', 107, '', '', NULL, '', 1, 2, 'workflow:category:query', 1, 1, 0, 0),
(109, '分类新增', 107, '', '', NULL, '', 2, 2, 'workflow:category:add', 1, 1, 0, 0),
(110, '分类编辑', 107, '', '', NULL, '', 3, 2, 'workflow:category:edit', 1, 1, 0, 0),
(111, '分类删除', 107, '', '', NULL, '', 4, 2, 'workflow:category:delete', 1, 1, 0, 0);

-- 流程实例菜单（sort_order=5）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(112, '流程实例', 100, '/workflow/instance', '/views/workflow/instance/index', NULL, 'Tickets', 5, 1, 'workflow:instance:list', 1, 1, 0, 0);

-- 流程实例按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(113, '实例查询', 112, '', '', NULL, '', 1, 2, 'workflow:instance:query', 1, 1, 0, 0),
(114, '发起流程', 112, '', '', NULL, '', 2, 2, 'workflow:instance:start', 1, 1, 0, 0),
(115, '取消流程', 112, '', '', NULL, '', 3, 2, 'workflow:instance:cancel', 1, 1, 0, 0);

-- 实例详情（hidden）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(125, '实例详情', 112, '/workflow/instance/detail', '/views/workflow/instance/InstanceDetail', NULL, '', 10, 1, NULL, 1, 0, 0, 0);

-- 待办任务菜单（sort_order=6）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(116, '待办任务', 100, '/workflow/task/todo', '/views/workflow/task/TodoTask', NULL, 'Bell', 6, 1, 'workflow:task:list', 1, 1, 0, 0);

-- 待办任务按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(117, '任务查询', 116, '', '', NULL, '', 1, 2, 'workflow:task:query', 1, 1, 0, 0),
(118, '任务认领', 116, '', '', NULL, '', 2, 2, 'workflow:task:claim', 1, 1, 0, 0),
(119, '任务完成', 116, '', '', NULL, '', 3, 2, 'workflow:task:complete', 1, 1, 0, 0),
(120, '任务委派', 116, '', '', NULL, '', 4, 2, 'workflow:task:delegate', 1, 1, 0, 0),
(121, '任务转办', 116, '', '', NULL, '', 5, 2, 'workflow:task:transfer', 1, 1, 0, 0),
(122, '任务退回', 116, '', '', NULL, '', 6, 2, 'workflow:task:return', 1, 1, 0, 0);

-- 任务详情（hidden）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(126, '任务详情', 116, '/workflow/task/detail', '/views/workflow/task/TaskDetail', NULL, '', 10, 1, NULL, 1, 0, 0, 0);

-- 已办任务菜单（sort_order=7）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(123, '已办任务', 100, '/workflow/task/done', '/views/workflow/task/DoneTask', NULL, 'Finished', 7, 1, 'workflow:task:list', 1, 1, 0, 0);

-- 抄送列表菜单（sort_order=8）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(240, '抄送列表', 100, '/workflow/copy', '/views/workflow/task/CopyTask', NULL, 'Message', 8, 1, 'workflow:task:list', 1, 1, 0, 0);

-- 表达式管理菜单（sort_order=10）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(220, '表达式管理', 100, '/workflow/expression', '/views/workflow/expression/index', NULL, 'Edit', 10, 1, 'workflow:expression:list', 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(221, '表达式查询', 220, '', '', NULL, '', 1, 2, 'workflow:expression:query', 1, 1, 0, 0),
(222, '表达式新增', 220, '', '', NULL, '', 2, 2, 'workflow:expression:add', 1, 1, 0, 0),
(223, '表达式编辑', 220, '', '', NULL, '', 3, 2, 'workflow:expression:edit', 1, 1, 0, 0),
(224, '表达式删除', 220, '', '', NULL, '', 4, 2, 'workflow:expression:delete', 1, 1, 0, 0);

-- 监听器管理菜单（sort_order=11）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(230, '监听器管理', 100, '/workflow/listener', '/views/workflow/listener/index', NULL, 'Bell', 11, 1, 'workflow:listener:list', 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(231, '监听器查询', 230, '', '', NULL, '', 1, 2, 'workflow:listener:query', 1, 1, 0, 0),
(232, '监听器新增', 230, '', '', NULL, '', 2, 2, 'workflow:listener:add', 1, 1, 0, 0),
(233, '监听器编辑', 230, '', '', NULL, '', 3, 2, 'workflow:listener:edit', 1, 1, 0, 0),
(234, '监听器删除', 230, '', '', NULL, '', 4, 2, 'workflow:listener:delete', 1, 1, 0, 0);

-- ========================================
-- Part 4: 角色授权（超级管理员拥有所有工作流菜单）
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 100), (1, 101), (1, 102), (1, 103), (1, 104), (1, 105), (1, 106), (1, 124),
(1, 107), (1, 108), (1, 109), (1, 110), (1, 111),
(1, 112), (1, 113), (1, 114), (1, 115), (1, 125),
(1, 116), (1, 117), (1, 118), (1, 119), (1, 120), (1, 121), (1, 122), (1, 126),
(1, 123), (1, 240),
(1, 200), (1, 201), (1, 202), (1, 203), (1, 204), (1, 205),
(1, 210), (1, 211), (1, 212), (1, 213), (1, 214), (1, 215), (1, 216),
(1, 220), (1, 221), (1, 222), (1, 223), (1, 224),
(1, 230), (1, 231), (1, 232), (1, 233), (1, 234);

-- ========================================
-- Part 5: 工作流字典数据
-- ========================================

-- 审批操作类型
INSERT INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(220, '审批操作类型', 'wf_action_type', 1, 1, '流程审批操作类型');

INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2201, 'wf_action_type', '提交',   'submit',       1,  1, 'primary'),
(2202, 'wf_action_type', '通过',   'approve',      2,  1, 'success'),
(2203, 'wf_action_type', '驳回',   'reject',       3,  1, 'danger'),
(2204, 'wf_action_type', '委派',   'delegate',     4,  1, 'warning'),
(2205, 'wf_action_type', '转办',   'transfer',     5,  1, 'warning'),
(2206, 'wf_action_type', '退回',   'return',       6,  1, 'info'),
(2207, 'wf_action_type', '撤回',   'withdraw',     7,  1, 'warning'),
(2208, 'wf_action_type', '抄送',   'copy',         8,  1, 'info'),
(2209, 'wf_action_type', '认领',   'claim',        9,  1, 'primary'),
(2210, 'wf_action_type', '取消',   'cancel',       10, 1, 'danger'),
(2211, 'wf_action_type', '加签',   'sign_create',  11, 1, 'primary'),
(2212, 'wf_action_type', '减签',   'sign_delete',  12, 1, 'primary');

-- 流程实例状态
INSERT INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(221, '流程实例状态', 'wf_instance_state', 1, 1, 'FlowLong流程实例状态');

INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2221, 'wf_instance_state', '暂停',    '-2',  1, 1, 'info'),
(2222, 'wf_instance_state', '暂存待审','-1',  2, 1, 'warning'),
(2223, 'wf_instance_state', '审批中',  '0',   3, 1, 'primary'),
(2224, 'wf_instance_state', '审批通过','1',   4, 1, 'success'),
(2225, 'wf_instance_state', '审批拒绝','2',   5, 1, 'danger'),
(2226, 'wf_instance_state', '撤销审批','3',   6, 1, 'warning'),
(2227, 'wf_instance_state', '超时结束','4',   7, 1, 'info'),
(2228, 'wf_instance_state', '强制终止','5',   8, 1, 'danger'),
(2229, 'wf_instance_state', '自动通过','6',   9, 1, 'success'),
(2230, 'wf_instance_state', '自动拒绝','7',   10, 1, 'danger');

-- AI审批决策类型
INSERT INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(222, 'AI审批决策', 'wf_ai_decision', 1, 1, 'AI审批决策结果');

INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2231, 'wf_ai_decision', '自动通过', 'APPROVE',  1, 1, 'success'),
(2232, 'wf_ai_decision', '自动拒绝', 'REJECT',   2, 1, 'danger'),
(2233, 'wf_ai_decision', '人工审批', 'MANUAL',   3, 1, 'warning');

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT '工作流模块初始化完成！' AS message;
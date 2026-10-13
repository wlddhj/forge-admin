-- ========================================
-- AI 多步 Agent 编排（LangGraph4j）第一期：内核 + 对话 Agent
-- 创建时间：2026-10-13
-- 说明：
--   1. 5 张编排业务表（含 tenant_id，租户隔离）
--   2. Agent 管理菜单与按钮权限（页面 331/334/336，按钮 332/333/335/337/338/339）
--   3. 授权给拥有 AI 管理（300）与智能对话（301）的角色
-- 幂等性：菜单/授权段可重复执行；建表段 IF NOT EXISTS
-- ========================================

-- 1. Agent 配置表
CREATE TABLE IF NOT EXISTS `ai_agent_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_code` varchar(50) NOT NULL COMMENT 'Agent编码',
  `agent_name` varchar(100) NOT NULL COMMENT 'Agent名称',
  `graph_key` varchar(50) NOT NULL COMMENT '编排图标识(代码定义)',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `system_prompt` text COMMENT '系统提示词',
  `user_prompt_template` text COMMENT '用户提示词模板',
  `model_bindings` json DEFAULT NULL COMMENT '步骤模型绑定(节点→modelId/modelType)',
  `max_steps` int NOT NULL DEFAULT 8 COMMENT '最大步数上限',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_code` (`agent_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent配置表';

-- 2. Agent 运行记录表
CREATE TABLE IF NOT EXISTS `ai_agent_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint NOT NULL COMMENT 'Agent配置ID',
  `conversation_id` bigint DEFAULT NULL COMMENT '关联会话ID',
  `user_id` bigint NOT NULL COMMENT '发起用户ID',
  `trigger_type` varchar(20) NOT NULL DEFAULT 'CONVERSATION' COMMENT '触发方式(CONVERSATION/API)',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态(RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED)',
  `last_node` varchar(100) DEFAULT NULL COMMENT '最后执行节点',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `input_summary` varchar(500) DEFAULT NULL COMMENT '输入摘要',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent运行记录表';

-- 3. Agent 运行步骤明细表
CREATE TABLE IF NOT EXISTS `ai_agent_run_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` bigint NOT NULL COMMENT '运行记录ID',
  `seq` int NOT NULL COMMENT '步骤序号',
  `node_name` varchar(100) NOT NULL COMMENT '节点名称',
  `step_type` varchar(20) NOT NULL COMMENT '步骤类型(LLM/TOOL/MULTIMODAL/INTERRUPT)',
  `model_id` bigint DEFAULT NULL COMMENT '使用的模型ID',
  `tool_name` varchar(50) DEFAULT NULL COMMENT '工具名称',
  `step_input` longtext COMMENT '输入内容(JSON)',
  `step_output` longtext COMMENT '输出内容(JSON)',
  `tokens_in` int DEFAULT 0 COMMENT '输入Token数',
  `tokens_out` int DEFAULT 0 COMMENT '输出Token数',
  `duration_ms` int DEFAULT 0 COMMENT '耗时(毫秒)',
  `status` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态(SUCCESS/FAILED/INTERRUPTED)',
  `error` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent运行步骤明细表';

-- 4. Agent 人工介入待办表
CREATE TABLE IF NOT EXISTS `ai_agent_todo` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` bigint NOT NULL COMMENT '运行记录ID',
  `title` varchar(200) NOT NULL COMMENT '待办标题',
  `content` longtext COMMENT '待办内容(待确认操作说明)',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/APPROVED/REJECTED)',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `handler_name` varchar(50) DEFAULT NULL COMMENT '处理人名称',
  `handle_comment` varchar(500) DEFAULT NULL COMMENT '处理意见',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent人工介入待办表';

-- 5. Agent 检查点表（LangGraph4j checkpoint 持久化）
CREATE TABLE IF NOT EXISTS `ai_agent_checkpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` bigint NOT NULL COMMENT '运行记录ID',
  `thread_id` varchar(64) NOT NULL COMMENT '线程ID',
  `checkpoint_id` varchar(64) NOT NULL COMMENT '检查点ID',
  `parent_id` varchar(64) DEFAULT NULL COMMENT '父检查点ID',
  `node``node` varchar(100) DEFAULT NULL COMMENT '节点名称',
  `next_node_id` varchar(100) DEFAULT NULL COMMENT '下一节点',
  `state``state` longtext COMMENT '状态快照(JSON)',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_thread_checkpoint` (`thread_id`, `checkpoint_id`),
  KEY `idx_run_id` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent检查点表';

-- 6. 菜单与按钮权限（331-339 已确认空闲）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 331 AND 339;
DELETE FROM `sys_menu` WHERE `id` BETWEEN 331 AND 339;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(331, 'Agent管理',  300, '/ai/agent/config', '/views/ai/agent/config/index', NULL, 'Monitor', 4, 1, 'ai:agent:list', 1, 1, 0, 0),
(332, 'Agent查询',  331, '', '', NULL, '', 1, 2, 'ai:agent:query',  1, 1, 0, 0),
(333, 'Agent配置',  331, '', '', NULL, '', 2, 2, 'ai:agent:config', 1, 1, 0, 0),
(334, '运行历史',  300, '/ai/agent/run', '/views/ai/agent/run/index', NULL, 'DataLine', 5, 1, 'ai:agent:list', 1, 1, 0, 0),
(335, '运行查询',  334, '', '', NULL, '', 1, 2, 'ai:agent:query', 1, 1, 0, 0),
(336, '人工待办',  300, '/ai/agent/todo', '/views/ai/agent/todo/index', NULL, 'Checked', 6, 1, 'ai:agent:list', 1, 1, 0, 0),
(337, '待办审批',  336, '', '', NULL, '', 1, 2, 'ai:agent:approve', 1, 1, 0, 0),
(338, '待办驳回',  336, '', '', NULL, '', 2, 2, 'ai:agent:reject',  1, 1, 0, 0),
(339, 'Agent对话', 301, '', '', NULL, '', 4, 2, 'ai:agent:run',  1, 1, 0, 0);

-- 7. 授权：拥有 AI 管理(300) 的角色授予 Agent 管理页面权限(331-338)；拥有智能对话(301) 的授予 Agent 对话(339)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
CROSS JOIN (SELECT 331 AS `id` UNION SELECT 332 UNION SELECT 333 UNION SELECT 334
            UNION SELECT 335 UNION SELECT 336 UNION SELECT 337 UNION SELECT 338) m
WHERE rm.`menu_id` = 300;

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 339
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 301;

-- 8. 内置默认 Agent 配置（编排图 conversational-assistant 代码定义）
INSERT INTO `ai_agent_config` (`agent_code`, `agent_name`, `graph_key`, `description`, `system_prompt`, `user_prompt_template`, `model_bindings`, `max_steps`, `status`, `remark`) VALUES
('conversational-assistant', '智能对话助手(Agent)', 'conversational-assistant',
 '多步编排对话Agent：图片理解 → 工具决策循环 → 高危确认 → 总结回答',
 '你是一个严谨的助手。判断是否需要工具：仅当问题需要查询数据库或抓取白名单网页等外部信息时才调用工具；自我介绍、常识问答等无需外部信息的问题，必须直接输出 final 回答。基于工具结果如实回答，不要编造。',
 '请处理用户请求：{input}',
 JSON_OBJECT('decision', JSON_OBJECT('modelType', 'text'), 'multimodal', JSON_OBJECT('modelType', 'multimodal'), 'summary', JSON_OBJECT('modelType', 'text')),
 8, 1, '内置默认Agent，图结构代码定义');

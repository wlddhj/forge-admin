-- ========================================
-- forge-admin AI模块初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建时间: 2026-06-17
-- 更新时间: 2026-07-09
-- 依赖文件: sql/init.sql
--
-- 与 Flyway 迁移的差异（说明）：
-- 1. 本脚本中的表结构与 com.forge.modules.ai.entity.* 一致（运行时真实期望）
-- 2. Flyway 脚本 V2026061701__ai_module_tables.sql 中的 schema 与 entity 存在
--    字段差异（如 ai_model_config 使用 api_url/entity 使用 api_endpoint），
--    实际生产部署以迁移为准并人工补齐 entity 期望的列；本脚本仅用于全新建库
-- 3. 菜单结构以 V2026061703__fix_ai_module_menu.sql 为准（删除了 305-308 对话管理
--    和 315-317 用量统计菜单）
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- ========================================
-- 1. AI模型配置表（与 AiModelConfig entity 一致）
-- ========================================
DROP TABLE IF EXISTS `ai_model_config`;
CREATE TABLE `ai_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `model_code` varchar(100) NOT NULL COMMENT '模型编码',
  `provider` varchar(50) NOT NULL COMMENT '服务商(deepseek/qwen/glm/openai)',
  `model_type` varchar(20) NOT NULL DEFAULT 'text' COMMENT '模型类型(text/multimodal/image/video/ocr/asr/tts/embedding)',
  `api_endpoint` varchar(255) DEFAULT NULL COMMENT 'API端点',
  `api_key` varchar(255) DEFAULT NULL COMMENT 'API密钥(加密存储)',
  `max_tokens` int DEFAULT 4096 COMMENT '最大Token数',
  `temperature` decimal(3,2) DEFAULT 0.7 COMMENT '温度参数',
  `context_window` int DEFAULT 8192 COMMENT '上下文窗口大小',
  `input_price` decimal(10,6) DEFAULT 0 COMMENT '输入价格(元/千Token)',
  `output_price` decimal(10,6) DEFAULT 0 COMMENT '输出价格(元/千Token)',
  `is_default` tinyint DEFAULT 0 COMMENT '是否默认模型(0:否 1:是)',
  `status` tinyint DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`),
  KEY `idx_provider` (`provider`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

-- ========================================
-- 2. AI对话会话表（与 AiConversation entity 一致）
-- ========================================
DROP TABLE IF EXISTS `ai_chat_conversation`;
CREATE TABLE `ai_chat_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `model_id` bigint DEFAULT NULL COMMENT '使用的模型ID',
  `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
  `summary` varchar(500) DEFAULT NULL COMMENT '会话摘要',
  `total_messages` int DEFAULT 0 COMMENT '消息总数',
  `total_tokens` int DEFAULT 0 COMMENT 'Token总数',
  `total_cost` decimal(10,4) DEFAULT 0 COMMENT '总费用(元)',
  `status` tinyint DEFAULT 1 COMMENT '状态(0:已关闭 1:进行中)',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话会话表';

-- ========================================
-- 3. AI对话消息表（与 AiMessage entity 一致）
-- ========================================
DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) NOT NULL COMMENT '消息ID',
  `conversation_id` varchar(64) NOT NULL COMMENT '会话ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `role` varchar(20) NOT NULL COMMENT '角色(user/assistant/system)',
  `content` text NOT NULL COMMENT '消息内容',
  `input_tokens` int DEFAULT 0 COMMENT '输入Token数',
  `output_tokens` int DEFAULT 0 COMMENT '输出Token数',
  `cost` decimal(10,4) DEFAULT 0 COMMENT '费用(元)',
  `model_id` bigint DEFAULT NULL COMMENT '使用的模型ID',
  `parent_message_id` varchar(64) DEFAULT NULL COMMENT '父消息ID',
  `metadata` json DEFAULT NULL COMMENT '元数据(请求参数等)',
  `status` tinyint DEFAULT 1 COMMENT '状态(0:失败 1:成功)',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_role` (`role`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话消息表';

-- ========================================
-- 4. AI文档表（与 AiDocument entity 一致）
-- ========================================
DROP TABLE IF EXISTS `ai_document`;
CREATE TABLE `ai_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `attachment_id` bigint DEFAULT NULL COMMENT '附件ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名称',
  `content` longtext COMMENT '提取的文本内容',
  `summary` text COMMENT 'AI生成的摘要',
  `model_name` varchar(100) DEFAULT NULL COMMENT '使用的模型名称',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态(0:待处理 1:处理中 2:已完成 3:失败)',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `model_provider` varchar(50) DEFAULT NULL COMMENT '处理模型提供商',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_attachment_id` (`attachment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文档表';

-- ========================================
-- 5. AI API用量统计表（与 AiApiUsage entity 一致）
-- 注：该 entity 当前无业务代码引用，保留以便后续接入用量统计功能
-- ========================================
DROP TABLE IF EXISTS `ai_api_usage`;
CREATE TABLE `ai_api_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `model_id` bigint DEFAULT NULL COMMENT '模型ID',
  `usage_date` date NOT NULL COMMENT '使用日期',
  `request_count` int DEFAULT 0 COMMENT '请求次数',
  `success_count` int DEFAULT 0 COMMENT '成功次数',
  `failure_count` int DEFAULT 0 COMMENT '失败次数',
  `total_input_tokens` int DEFAULT 0 COMMENT '总输入Token',
  `total_output_tokens` int DEFAULT 0 COMMENT '总输出Token',
  `total_cost` decimal(10,4) DEFAULT 0 COMMENT '总费用(元)',
  `avg_response_time` int DEFAULT 0 COMMENT '平均响应时间(ms)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_model_date` (`user_id`, `model_id`, `usage_date`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_usage_date` (`usage_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI API用量统计表';

-- ========================================
-- 6. AI Agent配置表（多步编排，LangGraph4j）
-- ========================================
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

-- ========================================
-- 7. AI Agent运行记录表
-- ========================================
CREATE TABLE IF NOT EXISTS `ai_agent_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint DEFAULT NULL COMMENT 'Agent配置ID(团队运行为NULL)',
  `team_id` bigint DEFAULT NULL COMMENT '团队ID(团队协作运行时)',
  `conversation_id` bigint DEFAULT NULL COMMENT '关联会话ID',
  `user_id` bigint NOT NULL COMMENT '发起用户ID',
  `trigger_type` varchar(20) NOT NULL DEFAULT 'CONVERSATION' COMMENT '触发方式(CONVERSATION/API)',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态(RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED)',
  `last_node` varchar(100) DEFAULT NULL COMMENT '最后执行节点',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `input_summary` varchar(500) DEFAULT NULL COMMENT '输入摘要',
  `final_answer` text COMMENT '最终回答',
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
  KEY `idx_team_id` (`team_id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent运行记录表';

-- ========================================
-- 8. AI Agent运行步骤明细表
-- ========================================
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

-- ========================================
-- 9. AI Agent人工介入待办表
-- ========================================
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

-- ========================================
-- 10. AI Agent检查点表（LangGraph4j checkpoint 持久化）
-- ========================================
CREATE TABLE IF NOT EXISTS `ai_agent_checkpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` bigint NOT NULL COMMENT '运行记录ID',
  `thread_id` varchar(64) NOT NULL COMMENT '线程ID',
  `checkpoint_id` varchar(64) NOT NULL COMMENT '检查点ID',
  `parent_id` varchar(64) DEFAULT NULL COMMENT '父检查点ID',
  `node` varchar(100) DEFAULT NULL COMMENT '节点名称',
  `next_node_id` varchar(100) DEFAULT NULL COMMENT '下一节点',
  `state` longtext COMMENT '状态快照(JSON)',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_thread_checkpoint` (`thread_id`, `checkpoint_id`),
  KEY `idx_run_id` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent检查点表';

-- ========================================
-- 初始化菜单数据（与 V2026061703__fix_ai_module_menu.sql 一致）
-- ========================================
-- AI管理目录（顶级目录）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(300, 'AI管理', 0, '/ai', 'Layout', '/ai/chat', 'MagicStick', 3, 0, NULL, 1, 1, 0, 0);

-- 智能对话菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(301, '智能对话', 300, '/ai/chat', '/views/ai/chat/index', NULL, 'ChatDotRound', 1, 1, 'ai:chat:list', 1, 1, 0, 0);

-- 智能对话按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(302, '对话查询', 301, '', '', NULL, '', 1, 2, 'ai:chat:query',   1, 1, 0, 0),
(303, '发送消息', 301, '', '', NULL, '', 2, 2, 'ai:chat:create',  1, 1, 0, 0),
(304, '删除对话', 301, '', '', NULL, '', 3, 2, 'ai:chat:delete',  1, 1, 0, 0),
(305, '对话编辑', 301, '', '', NULL, '', 5, 2, 'ai:chat:edit',    1, 1, 0, 0);

-- 文档管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(310, '文档管理', 300, '/ai/document', '/views/ai/document/index', NULL, 'Document', 2, 1, 'ai:document:list', 1, 1, 0, 0);

-- 文档管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(311, '文档查询', 310, '', '', NULL, '', 1, 2, 'ai:document:query',   1, 1, 0, 0),
(312, '上传文档', 310, '', '', NULL, '', 2, 2, 'ai:document:upload',  1, 1, 0, 0),
(313, '生成摘要', 310, '', '', NULL, '', 3, 2, 'ai:document:summary', 1, 1, 0, 0),
(314, '删除文档', 310, '', '', NULL, '', 4, 2, 'ai:document:delete',  1, 1, 0, 0);

-- 模型配置菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(320, '模型配置', 300, '/ai/model', '/views/ai/model/index', NULL, 'Setup', 3, 1, 'ai:model:list', 1, 1, 0, 0);

-- 模型配置按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(321, '模型查询', 320, '', '', NULL, '', 1, 2, 'ai:model:query',  1, 1, 0, 0),
(322, '配置管理', 320, '', '', NULL, '', 2, 2, 'ai:model:config', 1, 1, 0, 0),
(323, '切换模型', 320, '', '', NULL, '', 3, 2, 'ai:model:switch', 1, 1, 0, 0),
(324, '模型新增', 320, '', '', NULL, '', 4, 2, 'ai:model:add',    1, 1, 0, 0),
(325, '模型删除', 320, '', '', NULL, '', 5, 2, 'ai:model:delete', 1, 1, 0, 0);

-- ========================================
-- 角色菜单关联（超级管理员拥有所有AI菜单）
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
-- AI 主菜单
(1, 300),
-- 智能对话
(1, 301), (1, 302), (1, 303), (1, 304), (1, 305),
-- 文档管理
(1, 310), (1, 311), (1, 312), (1, 313), (1, 314),
-- 模型配置
(1, 320), (1, 321), (1, 322), (1, 323), (1, 324), (1, 325);

-- Agent 编排（页面 331/334/336 + 按钮 332/333/335/337/338/339）
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

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 331), (1, 332), (1, 333), (1, 334), (1, 335), (1, 336), (1, 337), (1, 338), (1, 339);

-- ========================================
-- 初始化内置 Agent 配置（编排图代码定义）
-- ========================================
INSERT INTO `ai_agent_config` (`agent_code`, `agent_name`, `graph_key`, `description`, `system_prompt`, `user_prompt_template`, `model_bindings`, `max_steps`, `status`, `remark`) VALUES
('conversational-assistant', '智能对话助手(Agent)', 'conversational-assistant',
 '多步编排对话Agent：图片理解 → 工具决策循环 → 高危确认 → 总结回答',
 '你是一个严谨的助手。判断是否需要工具：仅当问题需要查询数据库或抓取白名单网页等外部信息时才调用工具；自我介绍、常识问答等无需外部信息的问题，必须直接输出 final 回答。基于工具结果如实回答，不要编造。',
 '请处理用户请求：{input}',
 JSON_OBJECT('decision', JSON_OBJECT('modelType', 'text'), 'multimodal', JSON_OBJECT('modelType', 'multimodal'), 'summary', JSON_OBJECT('modelType', 'text')),
 8, 1, '内置默认Agent，图结构代码定义');

-- ========================================
-- AI 模型类型字典
-- ========================================
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
('AI模型类型', 'ai_model_type', 1, 1, 'AI模型能力类型');
INSERT INTO `sys_dict_data` (`dict_type`, `dict_label`, `dict_value`, `dict_sort`, `list_class`, `status`) VALUES
('ai_model_type', '文本生成',   'text',       1, 'primary', 1),
('ai_model_type', '多模态理解', 'multimodal', 2, 'success', 1),
('ai_model_type', '图像生成',   'image',      3, 'warning', 1),
('ai_model_type', '视频生成',   'video',      4, 'danger',  1),
('ai_model_type', 'OCR',        'ocr',        5, 'info',    1),
('ai_model_type', '语音识别',   'asr',        6, 'primary', 1),
('ai_model_type', '语音合成',   'tts',        7, 'success', 1),
('ai_model_type', '向量检索',   'embedding',  8, 'info',    1);

-- ========================================
-- 初始化模型配置数据
-- ========================================
INSERT INTO `ai_model_config` (`id`, `model_name`, `model_code`, `provider`, `api_endpoint`, `max_tokens`, `temperature`, `context_window`, `input_price`, `output_price`, `is_default`, `status`, `remark`) VALUES
(1, 'DeepSeek Chat',  'deepseek-chat',  'deepseek', 'https://api.deepseek.com/v1',                          4096, 0.7,  64000, 0.001,   0.002, 1, 1, 'DeepSeek通用对话模型(默认)'),
(2, 'DeepSeek Coder', 'deepseek-coder', 'deepseek', 'https://api.deepseek.com/v1',                          4096, 0.5,  16000, 0.001,   0.002, 0, 1, 'DeepSeek代码专用模型'),
(3, '通义千问 Turbo', 'qwen-turbo',     'qwen',     'https://dashscope.aliyuncs.com/compatible-mode/v1',    4096, 0.7,   8192, 0.002,   0.006, 0, 1, '阿里通义千问快速版'),
(4, '通义千问 Plus',  'qwen-plus',      'qwen',     'https://dashscope.aliyuncs.com/compatible-mode/v1',    4096, 0.7,  32768, 0.004,   0.012, 0, 1, '阿里通义千问增强版'),
(5, '智谱 GLM-4-Flash','glm-4-flash',  'glm',      'https://open.bigmodel.cn/api/paas/v4',                  4096, 0.7, 128000, 0.0001,  0.0001,0, 1, '智谱AI GLM-4极速版'),
(6, '文心一言 ERNIE-4.0','ernie-4.0-8k','ernie',   'https://qianfan.baidubce.com/v2',                       4096, 0.7,   8000, 0.012,   0.012, 0, 2, '百度千帆v2(OpenAI兼容)，需配置API Key后启用');
-- ========================================
-- 11. AI Agent定时任务表（后台自动化）
-- ========================================
CREATE TABLE IF NOT EXISTS `ai_agent_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_name` varchar(100) NOT NULL COMMENT '任务名称',
  `agent_code` varchar(50) DEFAULT NULL COMMENT 'Agent编码(与team_code二选一)',
  `team_code` varchar(50) DEFAULT NULL COMMENT '团队编码(与agent_code二选一，配了走团队协作图)',
  `sys_job_id` bigint DEFAULT NULL COMMENT '关联sys_job ID',
  `input_template` text COMMENT '输入模板(支持{date}/{yesterday}/{datetime}占位符)',
  `cron_expression` varchar(100) NOT NULL COMMENT 'cron表达式',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:停止 1:启用)',
  `last_run_id` bigint DEFAULT NULL COMMENT '最近运行记录ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_agent_code` (`agent_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent定时任务表';

-- ========================================
-- 12. AI Agent团队表（Supervisor 调度式多 Agent 协作）
-- ========================================
CREATE TABLE IF NOT EXISTS `ai_agent_team` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `team_code` varchar(50) NOT NULL COMMENT '团队编码',
  `team_name` varchar(100) NOT NULL COMMENT '团队名称',
  `description` varchar(500) DEFAULT NULL COMMENT '团队描述',
  `supervisor_prompt` text NOT NULL COMMENT 'Supervisor 系统提示词(含路由规则)',
  `model_bindings` json DEFAULT NULL COMMENT '模型绑定({"supervisor":{modelId|modelType},"summary":{...}})',
  `max_rounds` int NOT NULL DEFAULT 6 COMMENT 'Supervisor 最大调度轮次',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:停止 1:启用)',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_team_code` (`tenant_id`, `team_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent团队表';

CREATE TABLE IF NOT EXISTS `ai_agent_team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `team_id` bigint NOT NULL COMMENT '团队ID',
  `agent_id` bigint NOT NULL COMMENT 'Agent配置ID(ai_agent_config.id)',
  `member_role` varchar(200) NOT NULL COMMENT '成员职责描述(注入Supervisor路由提示词)',
  `allowed_tools` json DEFAULT NULL COMMENT '工具白名单(JSON数组,空=继承全部)',
  `max_steps` int DEFAULT NULL COMMENT '专家内最大循环步数(NULL=取Agent配置)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent团队成员表';

-- Agent 任务菜单（挂 AI 管理 300）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(341, 'Agent任务', 300, '/ai/agent/job', '/views/ai/agent/job/index', NULL, 'AlarmClock', 7, 1, 'ai:agent:job:list', 1, 1, 0, 0),
(342, '任务查询', 341, '', '', NULL, '', 1, 2, 'ai:agent:query',    1, 1, 0, 0),
(343, '任务配置', 341, '', '', NULL, '', 2, 2, 'ai:agent:job:config', 1, 1, 0, 0),
(344, '立即执行', 341, '', '', NULL, '', 3, 2, 'ai:agent:job:run',   1, 1, 0, 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 341), (1, 342), (1, 343), (1, 344);

-- Agent 团队菜单（挂 AI 管理 300，页面 2551 + 按钮 2552-2555）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2551, 'Agent团队', 300, '/ai/agent-team', '/views/ai/agent-team/index', NULL, 'User', 8, 1, 'ai:team:query',  1, 1, 0, 0),
(2552, '团队查询', 2551, '', '', NULL, '', 1, 2, 'ai:team:query',  1, 1, 0, 0),
(2553, '团队新增', 2551, '', '', NULL, '', 2, 2, 'ai:team:add',    1, 1, 0, 0),
(2554, '团队编辑', 2551, '', '', NULL, '', 3, 2, 'ai:team:edit',   1, 1, 0, 0),
(2555, '团队删除', 2551, '', '', NULL, '', 4, 2, 'ai:team:remove', 1, 1, 0, 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 2551), (1, 2552), (1, 2553), (1, 2554), (1, 2555);

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT 'AI模块初始化完成!' AS message;

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

USE `forge_admin`;

-- ========================================
-- 1. AI模型配置表（与 AiModelConfig entity 一致）
-- ========================================
DROP TABLE IF EXISTS `ai_model_config`;
CREATE TABLE `ai_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `model_code` varchar(100) NOT NULL COMMENT '模型编码',
  `provider` varchar(50) NOT NULL COMMENT '服务商(deepseek/qwen/glm/openai)',
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
(304, '删除对话', 301, '', '', NULL, '', 3, 2, 'ai:chat:delete',  1, 1, 0, 0);

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
(323, '切换模型', 320, '', '', NULL, '', 3, 2, 'ai:model:switch', 1, 1, 0, 0);

-- ========================================
-- 角色菜单关联（超级管理员拥有所有AI菜单）
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
-- AI 主菜单
(1, 300),
-- 智能对话
(1, 301), (1, 302), (1, 303), (1, 304),
-- 文档管理
(1, 310), (1, 311), (1, 312), (1, 313), (1, 314),
-- 模型配置
(1, 320), (1, 321), (1, 322), (1, 323);

-- ========================================
-- 初始化模型配置数据
-- ========================================
INSERT INTO `ai_model_config` (`id`, `model_name`, `model_code`, `provider`, `api_endpoint`, `max_tokens`, `temperature`, `context_window`, `input_price`, `output_price`, `is_default`, `status`, `remark`) VALUES
(1, 'DeepSeek Chat',  'deepseek-chat',  'deepseek', 'https://api.deepseek.com/v1/chat/completions',                                          4096, 0.7,  64000, 0.001,   0.002, 1, 1, 'DeepSeek通用对话模型(默认)'),
(2, 'DeepSeek Coder', 'deepseek-coder', 'deepseek', 'https://api.deepseek.com/v1/chat/completions',                                          4096, 0.5,  16000, 0.001,   0.002, 0, 1, 'DeepSeek代码专用模型'),
(3, '通义千问 Turbo', 'qwen-turbo',     'qwen',     'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation',     4096, 0.7,   8192, 0.002,   0.006, 0, 1, '阿里通义千问快速版'),
(4, '通义千问 Plus',  'qwen-plus',      'qwen',     'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation',     4096, 0.7,  32768, 0.004,   0.012, 0, 1, '阿里通义千问增强版'),
(5, '智谱 GLM-4-Flash','glm-4-flash',  'glm',      'https://open.bigmodel.cn/api/paas/v4/chat/completions',                               4096, 0.7, 128000, 0.0001,  0.0001,0, 1, '智谱AI GLM-4极速版');

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT 'AI模块初始化完成!' AS message;

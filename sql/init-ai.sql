-- ========================================
-- forge-admin AI模块初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建时间: 2026-06-17
-- 依赖文件: sql/init.sql
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `forge_admin`;

-- ========================================
-- 1. AI模型配置表
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
-- 2. AI对话会话表
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
-- 3. AI对话消息表
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
-- 4. AI文档表
-- ========================================
DROP TABLE IF EXISTS `ai_document`;
CREATE TABLE `ai_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名称',
  `file_path` varchar(500) DEFAULT NULL COMMENT '文件路径',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件URL',
  `file_type` varchar(100) DEFAULT NULL COMMENT '文件类型(MIME类型)',
  `file_size` bigint DEFAULT 0 COMMENT '文件大小(字节)',
  `content` longtext DEFAULT NULL COMMENT '文档内容',
  `summary` text DEFAULT NULL COMMENT '文档摘要',
  `model_name` varchar(100) DEFAULT NULL COMMENT '使用的模型名称',
  `status` tinyint DEFAULT 0 COMMENT '状态(0:待处理 1:处理中 2:已完成 3:失败)',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文档表';

-- ========================================
-- 5. AI API用量统计表
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
-- 初始化菜单数据 (AI模块完整菜单)
-- ========================================
-- AI助手主菜单 (目录)
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(300, 'AI助手', 0, '/ai', 'Layout', '/ai/model-config', 'MagicStick', 3, 0, NULL, 1, 1, 0, 0);

-- 模型配置管理 (菜单)
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(301, '模型配置', 300, '/ai/model-config', '/views/ai/model-config/index', NULL, 'Setting', 1, 1, 'ai:model-config:list', 1, 1, 0, 0),
(302, '模型查询', 301, '', '', NULL, '', 1, 2, 'ai:model-config:query', 1, 1, 0, 0),
(303, '模型新增', 301, '', '', NULL, '', 2, 2, 'ai:model-config:add', 1, 1, 0, 0),
(304, '模型编辑', 301, '', '', NULL, '', 3, 2, 'ai:model-config:edit', 1, 1, 0, 0),
(309, '模型删除', 301, '', '', NULL, '', 4, 2, 'ai:model-config:delete', 1, 1, 0, 0);

-- 对话管理 (菜单)
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(305, '对话管理', 300, '/ai/conversation', '/views/ai/conversation/index', NULL, 'ChatDotRound', 2, 1, 'ai:conversation:list', 1, 1, 0, 0),
(306, '对话查询', 305, '', '', NULL, '', 1, 2, 'ai:conversation:query', 1, 1, 0, 0),
(307, '对话删除', 305, '', '', NULL, '', 2, 2, 'ai:conversation:delete', 1, 1, 0, 0),
(308, '查看详情', 305, '', '', NULL, '', 3, 2, 'ai:conversation:detail', 1, 1, 0, 0);

-- 文档管理 (菜单)
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(310, '文档管理', 300, '/ai/document', '/views/ai/document/index', NULL, 'Document', 3, 1, 'ai:document:list', 1, 1, 0, 0),
(311, '文档查询', 310, '', '', NULL, '', 1, 2, 'ai:document:query', 1, 1, 0, 0),
(312, '文档上传', 310, '', '', NULL, '', 2, 2, 'ai:document:upload', 1, 1, 0, 0),
(313, '文档删除', 310, '', '', NULL, '', 3, 2, 'ai:document:delete', 1, 1, 0, 0),
(314, '文档分析', 310, '', '', NULL, '', 4, 2, 'ai:document:analyze', 1, 1, 0, 0);

-- API用量统计 (菜单)
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(315, '用量统计', 300, '/ai/api-usage', '/views/ai/api-usage/index', NULL, 'DataLine', 4, 1, 'ai:api-usage:list', 1, 1, 0, 0),
(316, '统计查询', 315, '', '', NULL, '', 1, 2, 'ai:api-usage:query', 1, 1, 0, 0),
(317, '导出报表', 315, '', '', NULL, '', 2, 2, 'ai:api-usage:export', 1, 1, 0, 0);

-- ========================================
-- 补充角色菜单关联 (超级管理员拥有所有AI菜单)
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
-- AI主菜单
(1, 300),
-- 模型配置管理
(1, 301), (1, 302), (1, 303), (1, 304), (1, 309),
-- 对话管理
(1, 305), (1, 306), (1, 307), (1, 308),
-- 文档管理
(1, 310), (1, 311), (1, 312), (1, 313), (1, 314),
-- API用量统计
(1, 315), (1, 316), (1, 317);

-- ========================================
-- 初始化模型配置数据
-- ========================================
INSERT INTO `ai_model_config` (`id`, `model_name`, `model_code`, `provider`, `api_endpoint`, `max_tokens`, `temperature`, `context_window`, `input_price`, `output_price`, `is_default`, `status`, `remark`) VALUES
(1, 'DeepSeek Chat', 'deepseek-chat', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 4096, 0.7, 64000, 0.001, 0.002, 1, 1, 'DeepSeek通用对话模型(默认)'),
(2, 'DeepSeek Coder', 'deepseek-coder', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 4096, 0.5, 16000, 0.001, 0.002, 0, 1, 'DeepSeek代码专用模型'),
(3, '通义千问 Turbo', 'qwen-turbo', 'qwen', 'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation', 4096, 0.7, 8192, 0.002, 0.006, 0, 1, '阿里通义千问快速版'),
(4, '通义千问 Plus', 'qwen-plus', 'qwen', 'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation', 4096, 0.7, 32768, 0.004, 0.012, 0, 1, '阿里通义千问增强版'),
(5, '智谱 GLM-4-Flash', 'glm-4-flash', 'glm', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', 4096, 0.7, 128000, 0.0001, 0.0001, 0, 1, '智谱AI GLM-4极速版');

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT 'AI模块初始化完成!' AS message;
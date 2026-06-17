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
-- 4. AIAPI用量统计表
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
-- 初始化菜单数据 (IDs 300-323)
-- ========================================
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
-- AI管理 (目录)
(300, 'AI管理', 0, '/ai', 'Layout', NULL, 'MagicStick', 3, 0, NULL, 1, 1, 0, 0),
-- 模型配置 (菜单)
(301, '模型配置', 300, '/ai/model-config', '/views/ai/model-config/index', NULL, 'Connection', 1, 1, 'ai:model-config:list', 1, 1, 0, 0),
-- 模型配置按钮
(302, '模型查询', 301, '', '', NULL, '', 1, 2, 'ai:model-config:query', 1, 1, 0, 0),
(303, '模型新增', 301, '', '', NULL, '', 2, 2, 'ai:model-config:add', 1, 1, 0, 0),
(304, '模型编辑', 301, '', '', NULL, '', 3, 2, 'ai:model-config:edit', 1, 1, 0, 0),
(305, '模型删除', 301, '', '', NULL, '', 4, 2, 'ai:model-config:delete', 1, 1, 0, 0),
(306, '设为默认', 301, '', '', NULL, '', 5, 2, 'ai:model-config:set-default', 1, 1, 0, 0),
(307, '模型导出', 301, '', '', NULL, '', 6, 2, 'ai:model-config:export', 1, 1, 0, 0),
-- 对话管理 (菜单)
(308, '对话管理', 300, '/ai/conversation', '/views/ai/conversation/index', NULL, 'ChatDotRound', 2, 1, 'ai:conversation:list', 1, 1, 0, 0),
-- 对话管理按钮
(309, '对话查询', 308, '', '', NULL, '', 1, 2, 'ai:conversation:query', 1, 1, 0, 0),
(310, '对话删除', 308, '', '', NULL, '', 2, 2, 'ai:conversation:delete', 1, 1, 0, 0),
(311, '对话导出', 308, '', '', NULL, '', 3, 2, 'ai:conversation:export', 1, 1, 0, 0),
(312, '关闭对话', 308, '', '', NULL, '', 4, 2, 'ai:conversation:close', 1, 1, 0, 0),
(313, '查看详情', 308, '', '', NULL, '', 5, 2, 'ai:conversation:detail', 1, 1, 0, 0),
-- 对话日志 (菜单 - 隐藏菜单，通过对话管理访问)
(314, '对话日志', 308, '/ai/chat-message', '/views/ai/chat-message/index', NULL, 'List', 6, 1, 'ai:chat-message:list', 1, 0, 0, 0),
-- 对话日志按钮
(315, '消息查询', 314, '', '', NULL, '', 1, 2, 'ai:chat-message:query', 1, 1, 0, 0),
(316, '消息删除', 314, '', '', NULL, '', 2, 2, 'ai:chat-message:delete', 1, 1, 0, 0),
(317, '消息导出', 314, '', '', NULL, '', 3, 2, 'ai:chat-message:export', 1, 1, 0, 0),
-- 用量统计 (菜单)
(318, '用量统计', 300, '/ai/api-usage', '/views/ai/api-usage/index', NULL, 'DataAnalysis', 3, 1, 'ai:api-usage:list', 1, 1, 0, 0),
-- 用量统计按钮
(319, '用量查询', 318, '', '', NULL, '', 1, 2, 'ai:api-usage:query', 1, 1, 0, 0),
(320, '用量导出', 318, '', '', NULL, '', 2, 2, 'ai:api-usage:export', 1, 1, 0, 0),
(321, '用量详情', 318, '', '', NULL, '', 3, 2, 'ai:api-usage:detail', 1, 1, 0, 0),
(322, '数据清空', 318, '', '', NULL, '', 4, 2, 'ai:api-usage:clear', 1, 1, 0, 0),
-- API密钥管理 (菜单)
(323, 'API密钥', 300, '/ai/api-key', '/views/ai/api-key/index', NULL, 'Key', 4, 1, 'ai:api-key:list', 1, 1, 0, 0);

-- ========================================
-- 初始化角色菜单关联
-- ========================================
-- 超级管理员(role_id=1)拥有所有AI模块菜单
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE `id` BETWEEN 300 AND 323;

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
-- ============================================================
-- 升级SQL：业务回调 SPI——wf_business_callback / wf_callback_record + 菜单权限
-- 日期：2026-10-27
-- 说明：业务系统订阅工作流事件（taskCreated/taskCompleted/instanceFinished），
--       平台向配置 URL 发送 HMAC-SHA256 签名的 HTTP POST（webhook）
-- 注意：dev 库未启用 Flyway，请在 forge_admin 库手工执行本脚本
-- ============================================================

USE forge_admin;

DROP TABLE IF EXISTS `wf_business_callback`;
CREATE TABLE `wf_business_callback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '回调名称',
    `url` VARCHAR(500) NOT NULL COMMENT '回调地址（http/https）',
    `secret` VARCHAR(100) DEFAULT NULL COMMENT '签名密钥（HMAC-SHA256）',
    `events` VARCHAR(200) NOT NULL COMMENT '订阅事件CSV：taskCreated,taskCompleted,instanceFinished',
    `process_key` VARCHAR(100) DEFAULT NULL COMMENT '流程标识过滤（空=全部流程）',
    `include_variables` TINYINT DEFAULT 0 COMMENT '是否随实例终态事件携带流程变量（0/1）',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用（0/1）',
    `timeout_ms` INT DEFAULT 5000 COMMENT 'HTTP 超时（毫秒）',
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务回调配置表';

DROP TABLE IF EXISTS `wf_callback_record`;
CREATE TABLE `wf_callback_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `callback_id` BIGINT NOT NULL COMMENT '回调配置ID',
    `callback_name` VARCHAR(100) DEFAULT NULL COMMENT '回调名称（快照）',
    `event_type` VARCHAR(30) NOT NULL COMMENT '事件类型（taskCreated/taskCompleted/instanceFinished）',
    `process_key` VARCHAR(100) DEFAULT NULL COMMENT '流程标识',
    `business_key` VARCHAR(100) DEFAULT NULL COMMENT '业务Key',
    `instance_id` BIGINT DEFAULT NULL COMMENT '流程实例ID',
    `task_id` BIGINT DEFAULT NULL COMMENT '任务ID（任务类事件）',
    `node_key` VARCHAR(100) DEFAULT NULL COMMENT '节点Key',
    `payload` TEXT DEFAULT NULL COMMENT '请求体JSON',
    `http_status` INT DEFAULT NULL COMMENT '最后响应HTTP状态码',
    `response_body` VARCHAR(2000) DEFAULT NULL COMMENT '最后响应体（截断）',
    `status` VARCHAR(10) NOT NULL COMMENT '投递状态（SUCCESS/FAILED）',
    `retry_count` INT DEFAULT 0 COMMENT '已重试次数',
    `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_callback` (`callback_id`),
    KEY `idx_instance` (`instance_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务回调投递记录表';

-- 业务回调菜单（流程管理 100 下，sort_order=9；id 使用空闲段 260-265）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(260, '业务回调', 100, '/workflow/callback', '/views/workflow/callback/index', NULL, 'Connection', 9, 1, 'workflow:callback:list', 1, 1, 0, 0);
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(261, '回调查询', 260, '', '', NULL, '', 1, 2, 'workflow:callback:query', 1, 1, 0, 0),
(262, '回调新增', 260, '', '', NULL, '', 2, 2, 'workflow:callback:add', 1, 1, 0, 0),
(263, '回调编辑', 260, '', '', NULL, '', 3, 2, 'workflow:callback:edit', 1, 1, 0, 0),
(264, '回调删除', 260, '', '', NULL, '', 4, 2, 'workflow:callback:delete', 1, 1, 0, 0);

-- 超管角色授权（role_id=1）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 260), (1, 261), (1, 262), (1, 263), (1, 264);

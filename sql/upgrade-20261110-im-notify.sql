-- ============================================================
-- 升级脚本：审批消息推送企业微信/钉钉（IM 工作通知，租户化配置 v2）
-- 日期：2026-11-10（v2 重写：配置从 yml+sys_config 迁移到按租户专用表）
-- 说明：dev 库无 Flyway，需手工执行本脚本；全部语句幂等，可重复执行。
--   v1 的 im.notify.enabled / im.notify.channels 两个 sys_config 键已废弃，
--   由本脚本清理；im.notify.web-base-url 保留（平台级跳转域名）。
--   渠道凭据与启停按租户维护：管理端「IM通知配置」页（secret 加密入库）。
-- ============================================================

-- 1. IM 渠道配置表（按租户维度，每租户每渠道一行）
CREATE TABLE IF NOT EXISTS `wf_im_channel_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（非租户模式视作租户1）',
    `channel` VARCHAR(32) NOT NULL COMMENT '渠道标识: wework/dingtalk',
    `enabled` TINYINT DEFAULT 0 COMMENT '是否启用(0/1)',
    `corp_id` VARCHAR(128) DEFAULT NULL COMMENT '企业微信 corpId（钉钉渠道为空）',
    `app_key` VARCHAR(128) DEFAULT NULL COMMENT '钉钉 appKey（企微渠道为空）',
    `agent_id` VARCHAR(64) NOT NULL COMMENT '应用 agentId',
    `secret` VARCHAR(512) NOT NULL COMMENT '应用 secret（AES-256-GCM 加密存储）',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:正常 1:已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_channel` (`tenant_id`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM通知渠道配置表（按租户维度）';

-- 2. 清理 v1 废弃的全局开关键（web-base-url 保留）
DELETE FROM sys_config WHERE config_key IN ('im.notify.enabled', 'im.notify.channels');

-- 3. 平台级跳转域名（幂等）
INSERT INTO sys_config (config_name, config_key, config_value, config_type, config_group, is_system, status, remark)
SELECT 'IM消息跳转域名', 'im.notify.web-base-url', 'http://localhost:3000', 'text', 'workflow', 0, 1, 'IM 消息点击跳转的前端地址（生产改为实际域名）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config x WHERE x.config_key = 'im.notify.web-base-url');

-- 4. IM通知配置页菜单（id 266-268 沿既有号段顺延）
INSERT INTO sys_menu (id, menu_name, parent_id, route_path, component_path, redirect_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached)
SELECT 266, 'IM通知配置', 100, '/workflow/im-config', '/views/workflow/imConfig/index', NULL, 'Promotion', 11, 1, 'workflow:im:config:list', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'workflow:im:config:list');

INSERT INTO sys_menu (id, menu_name, parent_id, route_path, component_path, redirect_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached)
SELECT 267, '配置保存', 266, '', '', NULL, '', 1, 2, 'workflow:im:config:edit', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'workflow:im:config:edit');

INSERT INTO sys_menu (id, menu_name, parent_id, route_path, component_path, redirect_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached)
SELECT 268, '配置删除', 266, '', '', NULL, '', 2, 2, 'workflow:im:config:delete', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'workflow:im:config:delete');

-- 5. 测试发送按钮权限（业务回调菜单下，v1 引入，此处兜底补齐）
INSERT INTO sys_menu (id, menu_name, parent_id, route_path, component_path, redirect_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached)
SELECT 265, 'IM通知测试', 260, '', '', NULL, '', 5, 2, 'workflow:im:test', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'workflow:im:test');

-- 6. 超级管理员授权（幂等）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (265, 266, 267, 268)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = 1 AND x.menu_id = m.id);

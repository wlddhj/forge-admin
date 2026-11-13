-- ========================================
-- 插件模块（plugin-module）升级 SQL —— 存量库手工执行
-- 内容：插件四表 / sys_menu 归属列 / 插件管理菜单与权限点 / License 巡检任务 / demo 存量菜单迁移
-- 全新安装走 sql/init.sql + sql/init-demo.sql（已含等价内容）
-- ========================================

-- 1) 插件注册表（全局共享表）
CREATE TABLE `sys_plugin` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plugin_code` VARCHAR(64) NOT NULL COMMENT '插件编码(全局唯一)',
    `name` VARCHAR(100) NOT NULL COMMENT '插件名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `provider` VARCHAR(100) DEFAULT NULL COMMENT '提供方',
    `running_version` VARCHAR(32) NOT NULL COMMENT '运行版本(启动刷新,变更记操作日志)',
    `global_status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0平台禁用(总闸)',
    `menus_synced` TINYINT NOT NULL DEFAULT 0 COMMENT '菜单已同步',
    `create_time` DATETIME DEFAULT NULL,
    `update_time` DATETIME DEFAULT NULL,
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '墓碑:删除时写id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin` (`plugin_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='插件注册表';

-- 2) 租户插件启停（全局共享表，tenant_id 显式列，已加入 ignore-tables）
CREATE TABLE `sys_plugin_tenant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `plugin_code` VARCHAR(64) NOT NULL COMMENT '插件编码',
    `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '0停用 1启用',
    `disabled_reason` VARCHAR(200) DEFAULT NULL COMMENT '停用原因(手动/license_expired/license_revoked)',
    `enabled_time` DATETIME DEFAULT NULL,
    `disabled_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT NULL,
    `update_time` DATETIME DEFAULT NULL,
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '墓碑:删除时写id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_plugin` (`tenant_id`, `plugin_code`, `deleted`),
    KEY `idx_plugin` (`plugin_code`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户插件启停';

-- 3) 插件 License（一码多插件拆行；License 不做任何版本约束——02 设计 Q5）
CREATE TABLE `sys_plugin_license` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `license_key` VARCHAR(64) NOT NULL COMMENT 'License编号(载荷内)',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `tenant_code` VARCHAR(64) NOT NULL COMMENT '冗余自载荷,防租户删除后失审计',
    `licensee_name` VARCHAR(200) DEFAULT NULL COMMENT '被授权方(公司主体,展示)',
    `plugin_code` VARCHAR(64) NOT NULL COMMENT '插件编码',
    `scopes` VARCHAR(500) DEFAULT NULL COMMENT 'License内置数据scope(CSV)',
    `not_before` DATETIME NOT NULL COMMENT '生效起(含)',
    `not_after` DATETIME NOT NULL COMMENT '生效止(含当日)',
    `issued_at` DATETIME DEFAULT NULL COMMENT '签发时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0已过期/撤销 2已替换',
    `verify_state` TINYINT NOT NULL DEFAULT 1 COMMENT '巡检结果 1通过 0未通过',
    `fail_reason` VARCHAR(200) DEFAULT NULL,
    `verify_time` DATETIME DEFAULT NULL,
    `payload_text` TEXT COMMENT 'License文件原文(复核)',
    `signature` TEXT COMMENT 'Base64签名(复核)',
    `create_time` DATETIME DEFAULT NULL,
    `update_time` DATETIME DEFAULT NULL,
    `create_by` BIGINT DEFAULT NULL,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '墓碑:删除时写id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_license` (`license_key`, `plugin_code`, `deleted`),
    KEY `idx_tenant_plugin` (`tenant_id`, `plugin_code`, `status`),
    KEY `idx_not_after` (`status`, `not_after`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='插件License';

-- 4) 管理端勾选授予的数据 scope（License 内置 scope 不落此表，校验时动态合并）
CREATE TABLE `sys_plugin_scope` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `plugin_code` VARCHAR(64) NOT NULL COMMENT '插件编码',
    `scope` VARCHAR(100) NOT NULL COMMENT 'scope码(如user:write,须∈插件声明集)',
    `grant_by` BIGINT DEFAULT NULL COMMENT '授予人',
    `grant_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT NULL,
    `update_time` DATETIME DEFAULT NULL,
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '墓碑:删除时写id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scope` (`tenant_id`, `plugin_code`, `scope`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='插件数据scope授权';

-- 5) sys_menu 归属列（插件菜单幂等同步键；递归 CTE 不改动）
ALTER TABLE `sys_menu`
    ADD COLUMN `plugin_code` VARCHAR(64) DEFAULT NULL COMMENT '归属插件编码(null=底座菜单)' AFTER `is_cached`,
    ADD COLUMN `plugin_menu_key` VARCHAR(100) DEFAULT NULL COMMENT '插件菜单节点键(同步幂等键)' AFTER `plugin_code`,
    ADD KEY `idx_plugin_menu` (`plugin_code`, `plugin_menu_key`);

-- 6) 插件管理菜单与权限点（系统管理目录 id=1 下；组件路径不带 /views 前缀）
INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`, `create_time`, `update_time`, `deleted`) VALUES
(2600, '插件管理', 1, '/system/plugin', 'system/plugin/index', NULL, 'Promotion', 95, 1, 'plugin:list', 1, 1, 0, 0, NOW(), NOW(), 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `menu_type`, `permission`, `sort_order`, `status`, `visible`, `is_external`, `is_cached`, `create_time`, `update_time`, `deleted`) VALUES
(2601, '插件启用', 2600, 2, 'plugin:enable',          1, 1, 1, 0, 0, NOW(), NOW(), 0),
(2602, '插件停用', 2600, 2, 'plugin:disable',         2, 1, 1, 0, 0, NOW(), NOW(), 0),
(2603, 'License导入', 2600, 2, 'plugin:license:import', 3, 1, 1, 0, 0, NOW(), NOW(), 0),
(2604, 'License撤销', 2600, 2, 'plugin:license:revoke', 4, 1, 1, 0, 0, NOW(), NOW(), 0),
(2605, '数据权限授予', 2600, 2, 'plugin:scope:grant',  5, 1, 1, 0, 0, NOW(), NOW(), 0),
(2606, '平台总闸', 2600, 2, 'plugin:global',           6, 1, 1, 0, 0, NOW(), NOW(), 0),
(2607, 'License在线签发', 2600, 2, 'plugin:license:issue', 7, 1, 1, 0, 0, NOW(), NOW(), 0);

-- 超管角色授权（role_id=1 SUPER_ADMIN）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 2600), (1, 2601), (1, 2602), (1, 2603), (1, 2604), (1, 2605), (1, 2606), (1, 2607);

-- 7) License 到期巡检任务（每日 02:30，错开日志清理 02:00）
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `status`, `concurrent`, `remark`) VALUES
(3, 'License到期巡检', 'SYSTEM', 'pluginLicenseCheckTask.execute', '0 30 2 * * ?', 1, 0, '过期授权置失效并自动停用对应租户插件');

-- 8) demo 存量菜单迁移：标记归属插件（保留 id 与角色关联，Runner 下一启动无缝接管）
UPDATE `sys_menu` SET `plugin_code` = 'demo', `plugin_menu_key` = 'demo-root'          WHERE `id` = 280 AND `deleted` = 0;
UPDATE `sys_menu` SET `plugin_code` = 'demo', `plugin_menu_key` = 'demo-project'       WHERE `id` = 281 AND `deleted` = 0;
UPDATE `sys_menu` SET `plugin_code` = 'demo', `plugin_menu_key` = 'demo-project-query'  WHERE `id` = 282 AND `deleted` = 0;
UPDATE `sys_menu` SET `plugin_code` = 'demo', `plugin_menu_key` = 'demo-project-add'    WHERE `id` = 283 AND `deleted` = 0;
UPDATE `sys_menu` SET `plugin_code` = 'demo', `plugin_menu_key` = 'demo-project-edit'   WHERE `id` = 284 AND `deleted` = 0;

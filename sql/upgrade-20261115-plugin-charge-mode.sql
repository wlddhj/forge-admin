-- ========================================
-- 插件收费模式（plugin-free-charge）升级 SQL —— 存量库手工执行
-- charge_mode: 1=收费授权(License 硬闸) 0=免费开放(免闸，仍需租户启用)；默认 1 兼容存量
-- 全新安装走 sql/init.sql（已含等价定义）
-- ========================================

ALTER TABLE `sys_plugin`
    ADD COLUMN `charge_mode` TINYINT NOT NULL DEFAULT 1 COMMENT '1收费授权 0免费开放(免License闸,仍需租户启用)' AFTER `global_status`;

-- 收费模式切换按钮（插件管理 2600 下）
INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `menu_type`, `permission`, `sort_order`, `status`, `visible`, `is_external`, `is_cached`, `create_time`, `update_time`, `deleted`) VALUES
(2608, '收费模式切换', 2600, 2, 'plugin:charge', 8, 1, 1, 0, 0, NOW(), NOW(), 0);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 2608);

-- ========================================
-- 补齐对话编辑按钮权限 ai:chat:edit（updateTitle 接口所需，此前菜单未配置导致所有人 403）
-- 创建时间：2026-10-14
-- ========================================

DELETE FROM `sys_role_menu` WHERE `menu_id` = 305;
DELETE FROM `sys_menu` WHERE `id` = 305;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(305, '对话编辑', 301, '', '', NULL, '', 5, 2, 'ai:chat:edit', 1, 1, 0, 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 305
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 301;

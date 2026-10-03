-- ========================================
-- App 用户菜单初始化脚本
-- 用于初始化移动端用户管理相关的菜单数据
-- ========================================

SET NAMES utf8mb4;

-- App 用户菜单（放在系统管理下）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(246, 'App用户', 1, '/system/app-user', '/views/system/app-user/index', NULL, 'Iphone', 9, 1, 'system:app-user:list', 1, 1, 0, 0),
(247, '详情', 246, '', '', NULL, '', 1, 2, 'system:app-user:detail', 1, 1, 0, 0),
(248, '修改', 246, '', '', NULL, '', 2, 2, 'system:app-user:update', 1, 1, 0, 0),
(249, '删除', 246, '', '', NULL, '', 3, 2, 'system:app-user:delete', 1, 1, 0, 0);

-- 为超级管理员角色添加 App 用户菜单权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 246),
(1, 247),
(1, 248),
(1, 249);

SELECT 'App用户菜单初始化完成' AS message;
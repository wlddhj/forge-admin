-- ========================================
-- OAuth2 客户端管理菜单及权限
-- ========================================

-- OAuth2客户端管理菜单（挂在系统管理下，sort_order=16，在附件管理之后）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(70, 'OAuth2客户端', 1, '/system/oauth2-client', '/views/system/oauth2-client/index', NULL, 'Key', 16, 1, 'system:oauth2-client:list', 1, 1, 0, 0);

-- OAuth2客户端管理权限按钮
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(71, '客户端查询', 70, '', '', NULL, '', 1, 2, 'system:oauth2-client:query', 1, 1, 0, 0),
(72, '客户新增', 70, '', '', NULL, '', 2, 2, 'system:oauth2-client:add', 1, 1, 0, 0),
(73, '客户端修改', 70, '', '', NULL, '', 3, 2, 'system:oauth2-client:edit', 1, 1, 0, 0),
(74, '客户端删除', 70, '', '', NULL, '', 4, 2, 'system:oauth2-client:delete', 1, 1, 0, 0);

-- 超级管理员角色授予新菜单权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 70), (1, 71), (1, 72), (1, 73), (1, 74);

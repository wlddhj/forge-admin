-- ========================================
-- 模型管理菜单及权限
-- ========================================

-- 模型管理菜单（挂在流程管理目录下，sort_order=2，在流程设计之后）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(210, '模型管理', 100, '/workflow/model', '/views/workflow/model/index', NULL, 'Files', 2, 1, 'workflow:model:list', 1, 1, 0, 0);

-- 模型管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(211, '模型查询', 210, '', '', NULL, '', 1, 2, 'workflow:model:query', 1, 1, 0, 0),
(212, '模型新增', 210, '', '', NULL, '', 2, 2, 'workflow:model:add', 1, 1, 0, 0),
(213, '模型编辑', 210, '', '', NULL, '', 3, 2, 'workflow:model:edit', 1, 1, 0, 0),
(214, '模型部署', 210, '', '', NULL, '', 4, 2, 'workflow:model:deploy', 1, 1, 0, 0),
(215, '模型删除', 210, '', '', NULL, '', 5, 2, 'workflow:model:delete', 1, 1, 0, 0),
(216, '模型设计', 210, '/workflow/model/designer', '/views/workflow/model/ModelDesigner', NULL, '', 10, 1, NULL, 1, 0, 0, 0);

-- 超级管理员角色授予新菜单权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 210), (1, 211), (1, 212), (1, 213), (1, 214), (1, 215), (1, 216);

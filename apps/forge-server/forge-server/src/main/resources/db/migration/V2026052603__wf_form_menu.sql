-- ========================================
-- 表单管理菜单及权限
-- ========================================

-- 表单管理菜单（挂在流程管理目录下，sort_order=1）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(200, '表单管理', 100, '/workflow/form', '/views/workflow/form/index', NULL, 'Document', 1, 1, 'workflow:form:list', 1, 1, 0, 0);

-- 表单设计器路由（hidden，不在侧边栏显示）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(201, '表单设计器', 200, '/workflow/form/editor', '/views/workflow/form/FormEditor', NULL, '', 1, 1, 'workflow:form:edit', 1, 0, 0, 0);

-- 表单管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(202, '表单查询', 200, '', '', NULL, '', 1, 2, 'workflow:form:query', 1, 1, 0, 0),
(203, '表单新增', 200, '', '', NULL, '', 2, 2, 'workflow:form:add', 1, 1, 0, 0),
(204, '表单编辑', 200, '', '', NULL, '', 3, 2, 'workflow:form:edit', 1, 1, 0, 0),
(205, '表单删除', 200, '', '', NULL, '', 4, 2, 'workflow:form:delete', 1, 1, 0, 0);

-- 超级管理员角色授予新菜单权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 200), (1, 201), (1, 202), (1, 203), (1, 204), (1, 205);
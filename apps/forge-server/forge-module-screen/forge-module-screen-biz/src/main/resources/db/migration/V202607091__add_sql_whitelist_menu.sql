-- ========================================
-- 大屏 SQL 白名单菜单及权限种子数据
-- ========================================
-- ID 段 2330-2339，位于数据源管理(2320)之后

-- SQL 白名单管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2330, 'SQL白名单', 2300, '/screen/sql-whitelist', '/views/screen/sql-whitelist/index', NULL, 'Lock', 3, 1, 'screen:sql-whitelist:list', 1, 1, 0, 0);

-- SQL 白名单按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2331, '白名单查询', 2330, '', '', NULL, '', 1, 2, 'screen:sql-whitelist:query', 1, 1, 0, 0),
(2332, '白名单新增', 2330, '', '', NULL, '', 2, 2, 'screen:sql-whitelist:add', 1, 1, 0, 0),
(2333, '白名单编辑', 2330, '', '', NULL, '', 3, 2, 'screen:sql-whitelist:edit', 1, 1, 0, 0),
(2334, '白名单删除', 2330, '', '', NULL, '', 4, 2, 'screen:sql-whitelist:remove', 1, 1, 0, 0);

-- 超级管理员角色授予 SQL 白名单全部权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 2330), (1, 2331), (1, 2332), (1, 2333), (1, 2334);

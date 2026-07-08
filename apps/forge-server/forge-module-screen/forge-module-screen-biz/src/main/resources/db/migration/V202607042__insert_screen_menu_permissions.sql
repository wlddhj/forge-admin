-- ========================================
-- 大屏模块菜单及权限种子数据
-- ========================================
-- 说明：
-- 1. 大屏访问/view 权限码采用动态格式 screen:screen:view:{code}，
--    每个大屏实例在创建时应自动生成对应权限码（T19 跟进项，当前 T15/T16 尚未实现，
--    暂不在此迁移中预置具体 view 权限）。
-- 2. publish/copy 权限码当前 T16 控制器复用 screen:screen:edit / screen:screen:add，
--    本迁移同时预置独立的 screen:screen:publish 与 screen:screen:copy 权限码，
--    以便后续按权限拆分时无需再补迁移脚本。
-- 3. ID 段使用 2300-2399，避开系统模块（<2000）、OAuth2(70-79)、AI(300-323)、工作流(2200+)。

-- 大屏管理目录（顶级目录，sort_order=10，位于工作流之后）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2300, '大屏管理', 0, '/screen', 'Layout', '/screen/index', 'Monitor', 10, 0, NULL, 1, 1, 0, 0);

-- 大屏管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2310, '大屏列表', 2300, '/screen/index', '/views/screen/index/index', NULL, 'Document', 1, 1, 'screen:screen:list', 1, 1, 0, 0);

-- 大屏管理按钮权限（list/query/add/edit/remove/publish/copy）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2311, '大屏查询', 2310, '', '', NULL, '', 1, 2, 'screen:screen:query', 1, 1, 0, 0),
(2312, '大屏新增', 2310, '', '', NULL, '', 2, 2, 'screen:screen:add', 1, 1, 0, 0),
(2313, '大屏编辑', 2310, '', '', NULL, '', 3, 2, 'screen:screen:edit', 1, 1, 0, 0),
(2314, '大屏删除', 2310, '', '', NULL, '', 4, 2, 'screen:screen:remove', 1, 1, 0, 0),
(2315, '大屏发布', 2310, '', '', NULL, '', 5, 2, 'screen:screen:publish', 1, 1, 0, 0),
(2316, '大屏复制', 2310, '', '', NULL, '', 6, 2, 'screen:screen:copy', 1, 1, 0, 0);
-- 注：screen:screen:list 已作为菜单(2310)本身的 permission；
--     screen:screen:view:{code} 为动态权限，由大屏实例创建时生成，不在本迁移中预置。

-- 数据源管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2320, '数据源管理', 2300, '/screen/data-source', '/views/screen/data-source/index', NULL, 'Connection', 2, 1, 'screen:data-source:list', 1, 1, 0, 0);

-- 数据源管理按钮权限（list/query/add/edit/remove/execute）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2321, '数据源查询', 2320, '', '', NULL, '', 1, 2, 'screen:data-source:query', 1, 1, 0, 0),
(2322, '数据源新增', 2320, '', '', NULL, '', 2, 2, 'screen:data-source:add', 1, 1, 0, 0),
(2323, '数据源编辑', 2320, '', '', NULL, '', 3, 2, 'screen:data-source:edit', 1, 1, 0, 0),
(2324, '数据源删除', 2320, '', '', NULL, '', 4, 2, 'screen:data-source:remove', 1, 1, 0, 0),
(2325, '数据源执行', 2320, '', '', NULL, '', 5, 2, 'screen:data-source:execute', 1, 1, 0, 0);

-- 超级管理员角色授予大屏模块全部菜单/按钮权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 2300),
(1, 2310), (1, 2311), (1, 2312), (1, 2313), (1, 2314), (1, 2315), (1, 2316),
(1, 2320), (1, 2321), (1, 2322), (1, 2323), (1, 2324), (1, 2325);

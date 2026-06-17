-- ========================================
-- 修复 AI 模块菜单及权限配置
-- ========================================

-- 1. 删除旧的 AI 相关菜单数据（包括角色关联）
DELETE FROM sys_role_menu WHERE menu_id >= 300 AND menu_id < 400;
DELETE FROM sys_menu WHERE id >= 300 AND id < 400;

-- 2. 重新插入正确的菜单和权限数据

-- AI管理目录（顶级目录）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(300, 'AI管理', 0, '/ai', 'Layout', '/ai/chat', 'MagicStick', 3, 0, NULL, 1, 1, 0, 0);

-- 智能对话菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(301, '智能对话', 300, '/ai/chat', '/views/ai/chat/index', NULL, 'ChatDotRound', 1, 1, 'ai:chat:list', 1, 1, 0, 0);

-- 对话管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(302, '对话查询', 301, '', '', NULL, '', 1, 2, 'ai:chat:query', 1, 1, 0, 0),
(303, '发送消息', 301, '', '', NULL, '', 2, 2, 'ai:chat:create', 1, 1, 0, 0),
(304, '删除对话', 301, '', '', NULL, '', 3, 2, 'ai:chat:delete', 1, 1, 0, 0);

-- 文档管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(310, '文档管理', 300, '/ai/document', '/views/ai/document/index', NULL, 'Document', 2, 1, 'ai:document:list', 1, 1, 0, 0);

-- 文档管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(311, '文档查询', 310, '', '', NULL, '', 1, 2, 'ai:document:query', 1, 1, 0, 0),
(312, '上传文档', 310, '', '', NULL, '', 2, 2, 'ai:document:upload', 1, 1, 0, 0),
(313, '生成摘要', 310, '', '', NULL, '', 3, 2, 'ai:document:summary', 1, 1, 0, 0),
(314, '删除文档', 310, '', '', NULL, '', 4, 2, 'ai:document:delete', 1, 1, 0, 0);

-- 模型配置菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(320, '模型配置', 300, '/ai/model', '/views/ai/model/index', NULL, 'Setup', 3, 1, 'ai:model:list', 1, 1, 0, 0);

-- 模型配置按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(321, '模型查询', 320, '', '', NULL, '', 1, 2, 'ai:model:query', 1, 1, 0, 0),
(322, '配置管理', 320, '', '', NULL, '', 2, 2, 'ai:model:config', 1, 1, 0, 0),
(323, '切换模型', 320, '', '', NULL, '', 3, 2, 'ai:model:switch', 1, 1, 0, 0);

-- 3. 超级管理员角色授予所有AI模块菜单权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 300), (1, 301), (1, 302), (1, 303), (1, 304),
(1, 310), (1, 311), (1, 312), (1, 313), (1, 314),
(1, 320), (1, 321), (1, 322), (1, 323);
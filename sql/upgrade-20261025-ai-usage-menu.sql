-- ============================================================
-- AI 用量统计菜单与权限 | 2026-10-25
-- 页面: views/ai/usage/index.vue;权限: ai:usage:query
-- 挂 AI 管理目录(id=300,init-ai.sql 固定值);幂等:permission 不存在才插入
-- ============================================================

INSERT INTO sys_menu (menu_name, parent_id, route_path, component_path, redirect_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached, create_time)
SELECT '用量统计', m.id, '/ai/usage', '/views/ai/usage/index', NULL, 'DataAnalysis', 90, 1, 'ai:usage:query', 1, 1, 0, 1, NOW()
FROM sys_menu m
WHERE m.menu_name = 'AI管理' AND m.parent_id = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'ai:usage:query')
LIMIT 1;

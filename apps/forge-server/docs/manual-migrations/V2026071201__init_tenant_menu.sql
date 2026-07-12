-- =============================================================================
-- 多租户管理菜单初始化（平台超管可见）
-- =============================================================================
-- 在"系统管理"目录下新增"租户管理"和"套餐管理"两个菜单
-- ID 段使用 2400-2499 避免与现有菜单冲突
--
-- 注意：菜单 SQL 不受 Flyway 管理（本项目未启用），手动执行：
--   mysql -u root -p forge_admin < V2026071201__init_tenant_menu.sql
--
-- 幂等性：使用 INSERT IGNORE，重跑不会重复插入
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 租户管理菜单（parent_id=1 系统管理）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, menu_name, parent_id, route_path, component_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached, create_time, update_time, deleted)
VALUES (2400, '租户管理', 1, '/system/tenant', 'system/tenant/index', 'OfficeBuilding', 90, 1, 'system:tenant:list', 1, 1, 0, 0, NOW(), NOW(), 0);

-- 租户管理按钮权限
INSERT IGNORE INTO sys_menu (id, menu_name, parent_id, menu_type, permission, sort_order, status, visible, is_external, is_cached, create_time, update_time, deleted)
VALUES
(2401, '租户查询', 2400, 2, 'system:tenant:query', 1, 1, 1, 0, 0, NOW(), NOW(), 0),
(2402, '租户新增', 2400, 2, 'system:tenant:add',  2, 1, 1, 0, 0, NOW(), NOW(), 0),
(2403, '租户修改', 2400, 2, 'system:tenant:update', 3, 1, 1, 0, 0, NOW(), NOW(), 0),
(2404, '租户删除', 2400, 2, 'system:tenant:delete', 4, 1, 1, 0, 0, NOW(), NOW(), 0),
(2405, '状态切换', 2400, 2, 'system:tenant:status', 5, 1, 1, 0, 0, NOW(), NOW(), 0);

-- ---------------------------------------------------------------------------
-- 2. 套餐管理菜单（parent_id=1 系统管理）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, menu_name, parent_id, route_path, component_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached, create_time, update_time, deleted)
VALUES (2410, '套餐管理', 1, '/system/tenant-package', 'system/tenant-package/index', 'Box', 91, 1, 'system:tenant-package:list', 1, 1, 0, 0, NOW(), NOW(), 0);

-- 套餐管理按钮权限
INSERT IGNORE INTO sys_menu (id, menu_name, parent_id, menu_type, permission, sort_order, status, visible, is_external, is_cached, create_time, update_time, deleted)
VALUES
(2411, '套餐查询',   2410, 2, 'system:tenant-package:query',  1, 1, 1, 0, 0, NOW(), NOW(), 0),
(2412, '套餐新增',   2410, 2, 'system:tenant-package:add',    2, 1, 1, 0, 0, NOW(), NOW(), 0),
(2413, '套餐修改',   2410, 2, 'system:tenant-package:update', 3, 1, 1, 0, 0, NOW(), NOW(), 0),
(2414, '套餐删除',   2410, 2, 'system:tenant-package:delete', 4, 1, 1, 0, 0, NOW(), NOW(), 0);

-- ---------------------------------------------------------------------------
-- 3. 创建默认套餐并绑定菜单（可选，建议执行）
-- ---------------------------------------------------------------------------
-- 把所有现有菜单（除了租户管理本身）都加入"默认套餐"(id=1)
-- 平台超管可以后续在套餐管理界面调整
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_tenant_package_menu (tenant_package_id, menu_id)
SELECT 1, id FROM sys_menu
WHERE id NOT IN (2400, 2401, 2402, 2403, 2404, 2405, 2410, 2411, 2412, 2413, 2414)
  AND deleted = 0;

-- 同时把租户管理与套餐管理也加入默认套餐
INSERT IGNORE INTO sys_tenant_package_menu (tenant_package_id, menu_id) VALUES
(1, 2400), (1, 2401), (1, 2402), (1, 2403), (1, 2404), (1, 2405),
(1, 2410), (1, 2411), (1, 2412), (1, 2413), (1, 2414);

-- ---------------------------------------------------------------------------
-- 4. 确保"默认租户"(id=1)绑定"默认套餐"(id=1)
-- ---------------------------------------------------------------------------
UPDATE sys_tenant SET package_id = 1 WHERE id = 1 AND package_id IS NULL;

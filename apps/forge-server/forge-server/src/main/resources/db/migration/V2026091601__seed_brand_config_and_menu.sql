-- ========================================
-- 品牌配置种子数据 + 品牌设置菜单
-- 创建时间：2026-09-16
-- 说明：Logo、项目名称、登录页主副标题可配置化
--   sys.system.name 已存在（值为 forge-admin），不重复插入
-- 菜单 ID 段分配：2540-2541（避开大屏 2300-2399、租户 2400-2499、打印 2500-2530）
-- ========================================

-- ========================================
-- 品牌配置 key（幂等：已存在则跳过）
-- ========================================
INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`)
SELECT '品牌Logo', 'sys.brand.logo', '', 'text', 'brand', 1, 1, '品牌Logo图片URL，空则使用系统默认 /logo.svg'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.brand.logo' AND `deleted` = 0);

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`)
SELECT '登录页主标题', 'sys.brand.login.title', 'forge-admin', 'text', 'brand', 1, 1, '登录页主标题，空则前端回退构建默认值'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.brand.login.title' AND `deleted` = 0);

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`)
SELECT '登录页副标题', 'sys.brand.login.subtitle', '聚能后台管理系统', 'text', 'brand', 1, 1, '登录页副标题，空则前端回退构建默认值'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.brand.login.subtitle' AND `deleted` = 0);

-- ========================================
-- 菜单与权限码种子
-- ========================================

-- 品牌设置菜单（系统管理下，sort_order=9，位于 OAuth2客户端 之后）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`)
SELECT 2540, '品牌设置', 1, '/system/brand', '/views/system/brand/index', NULL, 'Brush', 9, 1, 'system:brand:query', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 2540);

-- 品牌保存按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`)
SELECT 2541, '品牌保存', 2540, '', '', NULL, '', 1, 2, 'system:brand:update', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 2541);

-- ========================================
-- 超级管理员角色授权（权限来自 sys_role_menu 关联，无超管全权限硬编码）
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`id` IN (2540, 2541)
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND r.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
  );

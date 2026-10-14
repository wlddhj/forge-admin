-- ========================================
-- AI Agent SQL 工具租户范围治理
-- 创建时间：2026-10-14
-- 说明：
--   1. SQL 工具表白名单（跨租户共享，物理删除）
--   2. 白名单管理菜单与按钮权限（页面 340，按钮 341-344）
--   3. 授权给拥有 AI 管理（300）的角色
-- 幂等性：菜单/授权段可重复执行；建表段 IF NOT EXISTS；种子段先删后插
-- ========================================

-- 1. SQL 工具表白名单表（跨租户共享配置，需加入 forge.tenant.ignore-tables）
CREATE TABLE IF NOT EXISTS `ai_sql_whitelist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `table_name` varchar(64) NOT NULL COMMENT '表名(小写)',
  `table_comment` varchar(255) DEFAULT NULL COMMENT '表注释(展示给Agent)',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_sql_whitelist_table` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent SQL工具表白名单';

-- 2. 初始种子：仅预置低敏租户业务表，sys_ 系敏感表由管理员按需开通。
--    INSERT IGNORE 幂等：已存在（含管理员修改过的）不覆盖
INSERT IGNORE INTO `ai_sql_whitelist` (`table_name`, `table_comment`, `enabled`, `remark`) VALUES
('ai_conversation', 'AI会话表', 1, '初始种子'),
('ai_document', 'AI文档表', 1, '初始种子');

-- 3. 菜单与按钮权限（340-344 已确认空闲）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 340 AND 344;
DELETE FROM `sys_menu` WHERE `id` BETWEEN 340 AND 344;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(340, 'SQL白名单', 300, '/ai/sql-whitelist', '/views/ai/sql-whitelist/index', NULL, 'Lock', 7, 1, 'ai:sql-whitelist:list', 1, 1, 0, 0),
(341, '白名单查询', 340, '', '', NULL, '', 1, 2, 'ai:sql-whitelist:query', 1, 1, 0, 0),
(342, '白名单新增', 340, '', '', NULL, '', 2, 2, 'ai:sql-whitelist:add', 1, 1, 0, 0),
(343, '白名单编辑', 340, '', '', NULL, '', 3, 2, 'ai:sql-whitelist:edit', 1, 1, 0, 0),
(344, '白名单删除', 340, '', '', NULL, '', 4, 2, 'ai:sql-whitelist:remove', 1, 1, 0, 0);

-- 4. 授权：拥有 AI 管理(300) 的角色授予 SQL 白名单全部权限(340-344)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
CROSS JOIN (SELECT 340 AS `id` UNION SELECT 341 UNION SELECT 342 UNION SELECT 343 UNION SELECT 344) m
WHERE rm.`menu_id` = 300;

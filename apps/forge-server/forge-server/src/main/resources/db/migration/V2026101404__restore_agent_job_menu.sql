-- ========================================
-- 修复：恢复 Agent任务 菜单
-- 创建时间：2026-10-14
-- 原因：V2026101402 核查菜单 ID 段时遗漏 V2026101302 已占用的 341-344（Agent任务），
--       DELETE 340-344 误删 Agent任务 菜单并被 SQL 白名单按钮覆盖同段。
--       本脚本将 Agent任务 重建到空闲段 346-349。
-- 幂等性：可重复执行
-- ========================================

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 346 AND 349;
DELETE FROM `sys_menu` WHERE `id` BETWEEN 346 AND 349;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(346, 'Agent任务', 300, '/ai/agent/job', '/views/ai/agent/job/index', NULL, 'AlarmClock', 8, 1, 'ai:agent:job:list', 1, 1, 0, 0),
(347, '任务查询', 346, '', '', NULL, '', 1, 2, 'ai:agent:query',    1, 1, 0, 0),
(348, '任务配置', 346, '', '', NULL, '', 2, 2, 'ai:agent:job:config', 1, 1, 0, 0),
(349, '立即执行', 346, '', '', NULL, '', 3, 2, 'ai:agent:job:run',   1, 1, 0, 0);

-- 授权：拥有 AI 管理(300) 的角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
CROSS JOIN (SELECT 346 AS `id` UNION SELECT 347 UNION SELECT 348 UNION SELECT 349) m
WHERE rm.`menu_id` = 300;

-- ========================================
-- AI Agent 后台自动化：定时任务定义表 + 菜单权限
-- 创建时间：2026-10-13
-- 说明：
--   1. ai_agent_job：Agent 任务定义（绑定 agentCode + 输入模板 + cron），联动 sys_job 调度
--   2. 菜单 341-344（Agent任务页面 + 查询/配置/立即执行按钮），授权给拥有 AI 管理(300) 的角色
-- 幂等性：菜单/授权段可重复执行；建表段 IF NOT EXISTS
-- ========================================

CREATE TABLE IF NOT EXISTS `ai_agent_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_name` varchar(100) NOT NULL COMMENT '任务名称',
  `agent_code` varchar(50) NOT NULL COMMENT 'Agent编码',
  `sys_job_id` bigint DEFAULT NULL COMMENT '关联sys_job ID',
  `input_template` text COMMENT '输入模板(支持{date}/{yesterday}/{datetime}占位符)',
  `cron_expression` varchar(100) NOT NULL COMMENT 'cron表达式',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:停止 1:启用)',
  `last_run_id` bigint DEFAULT NULL COMMENT '最近运行记录ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_agent_code` (`agent_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent定时任务表';

-- 菜单与按钮权限（341-344 已确认空闲）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 341 AND 344;
DELETE FROM `sys_menu` WHERE `id` BETWEEN 341 AND 344;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(341, 'Agent任务', 300, '/ai/agent/job', '/views/ai/agent/job/index', NULL, 'AlarmClock', 7, 1, 'ai:agent:job:list', 1, 1, 0, 0),
(342, '任务查询', 341, '', '', NULL, '', 1, 2, 'ai:agent:query',    1, 1, 0, 0),
(343, '任务配置', 341, '', '', NULL, '', 2, 2, 'ai:agent:job:config', 1, 1, 0, 0),
(344, '立即执行', 341, '', '', NULL, '', 3, 2, 'ai:agent:job:run',   1, 1, 0, 0);

-- 授权：拥有 AI 管理(300) 的角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
CROSS JOIN (SELECT 341 AS `id` UNION SELECT 342 UNION SELECT 343 UNION SELECT 344) m
WHERE rm.`menu_id` = 300;

-- ========================================
-- AI 多 Agent 子图协作：Agent 团队表 + 菜单权限
-- 创建时间：2026-10-15
-- 说明：
--   1. ai_agent_team：Agent 团队定义（Supervisor 提示词 + 模型绑定 + 轮次上限）
--   2. ai_agent_team_member：团队成员（引用 ai_agent_config，含职责/工具白名单/循环上限）
--   3. ai_agent_run 加 team_id（agent_id 改可空）；ai_agent_job 加 team_code（agent_code 改可空）
--   4. 菜单 2551-2555（Agent团队页面 + 查询/新增/编辑/删除按钮），授权给拥有 AI 管理(300) 的角色
-- 幂等性：菜单/授权段可重复执行；建表段 IF NOT EXISTS；ALTER 段非幂等，重复执行报错可忽略
-- ========================================

CREATE TABLE IF NOT EXISTS `ai_agent_team` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `team_code` varchar(50) NOT NULL COMMENT '团队编码',
  `team_name` varchar(100) NOT NULL COMMENT '团队名称',
  `description` varchar(500) DEFAULT NULL COMMENT '团队描述',
  `supervisor_prompt` text NOT NULL COMMENT 'Supervisor 系统提示词(含路由规则)',
  `model_bindings` json DEFAULT NULL COMMENT '模型绑定({"supervisor":{modelId|modelType},"summary":{...}})',
  `max_rounds` int NOT NULL DEFAULT 6 COMMENT 'Supervisor 最大调度轮次',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:停止 1:启用)',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_team_code` (`tenant_id`, `team_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent团队表';

CREATE TABLE IF NOT EXISTS `ai_agent_team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `team_id` bigint NOT NULL COMMENT '团队ID',
  `agent_id` bigint NOT NULL COMMENT 'Agent配置ID(ai_agent_config.id)',
  `member_role` varchar(200) NOT NULL COMMENT '成员职责描述(注入Supervisor路由提示词)',
  `allowed_tools` json DEFAULT NULL COMMENT '工具白名单(JSON数组,空=继承全部)',
  `max_steps` int DEFAULT NULL COMMENT '专家内最大循环步数(NULL=取Agent配置)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent团队成员表';

-- 运行记录：团队运行 agent_id 为空、team_id 指向团队
ALTER TABLE `ai_agent_run` ADD COLUMN `team_id` bigint DEFAULT NULL COMMENT '团队ID(团队协作运行时)' AFTER `agent_id`;
ALTER TABLE `ai_agent_run` ADD INDEX `idx_team_id` (`team_id`);
ALTER TABLE `ai_agent_run` MODIFY `agent_id` bigint DEFAULT NULL COMMENT 'Agent配置ID(团队运行为NULL)';

-- 定时任务：agent_code 与 team_code 二选一（应用层校验）
ALTER TABLE `ai_agent_job` ADD COLUMN `team_code` varchar(50) DEFAULT NULL COMMENT '团队编码(与agent_code二选一)' AFTER `agent_code`;
ALTER TABLE `ai_agent_job` MODIFY `agent_code` varchar(50) DEFAULT NULL COMMENT 'Agent编码(与team_code二选一)';

-- 菜单与按钮权限（2551-2555 已确认空闲：当前库最大菜单ID 2541）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 2551 AND 2555;
DELETE FROM `sys_menu` WHERE `id` BETWEEN 2551 AND 2555;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2551, 'Agent团队', 300, '/ai/agent-team', '/views/ai/agent-team/index', NULL, 'User', 8, 1, 'ai:team:query',  1, 1, 0, 0),
(2552, '团队查询', 2551, '', '', NULL, '', 1, 2, 'ai:team:query',  1, 1, 0, 0),
(2553, '团队新增', 2551, '', '', NULL, '', 2, 2, 'ai:team:add',    1, 1, 0, 0),
(2554, '团队编辑', 2551, '', '', NULL, '', 3, 2, 'ai:team:edit',   1, 1, 0, 0),
(2555, '团队删除', 2551, '', '', NULL, '', 4, 2, 'ai:team:remove', 1, 1, 0, 0);

-- 授权：拥有 AI 管理(300) 的角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
CROSS JOIN (SELECT 2551 AS `id` UNION SELECT 2552 UNION SELECT 2553 UNION SELECT 2554 UNION SELECT 2555) m
WHERE rm.`menu_id` = 300;

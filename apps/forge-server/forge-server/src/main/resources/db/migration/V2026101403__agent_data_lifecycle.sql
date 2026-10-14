-- ========================================
-- Agent 运行数据生命周期管理
-- 创建时间：2026-10-14
-- 说明：
--   1. 保留期参数 4 条（sys_config，config_group=ai）
--   2. 定时清理任务（sys_job，每日 03:00，禁止并发）
--   3. 手动清理按钮权限（sys_menu 345，挂 Agent管理 331 下）
--   4. create_time 索引（清理按时间扫描，防大表首次清理全表扫）
-- 幂等性：INSERT IGNORE（config_key 唯一）；sys_job DELETE+INSERT；授权段可重复执行
--          索引段 ALTER 非幂等，重复执行报 Duplicate key name 可忽略
-- ========================================

-- 0. 清理扫描索引
ALTER TABLE `ai_agent_run` ADD INDEX `idx_create_time` (`create_time`);
ALTER TABLE `ai_agent_run_step` ADD INDEX `idx_create_time` (`create_time`);
ALTER TABLE `ai_agent_todo` ADD INDEX `idx_create_time` (`create_time`);
ALTER TABLE `ai_agent_checkpoint` ADD INDEX `idx_create_time` (`create_time`);

-- 1. 保留期参数
INSERT IGNORE INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`) VALUES
('Agent运行记录保留天数', 'ai.agent.retention.run-days', '30', 'number', 'ai', 1, 1, '已完结(RUNNING/WAITING_APPROVAL除外)运行记录保留天数，超期物理删除'),
('Agent运行步骤保留天数', 'ai.agent.retention.step-days', '30', 'number', 'ai', 1, 1, '运行步骤明细(含大字段)保留天数，按 create_time 超期物理删除'),
('Agent待办保留天数', 'ai.agent.retention.todo-days', '30', 'number', 'ai', 1, 1, '人工待办保留天数，PENDING 状态除外，超期物理删除'),
('Agent检查点保留天数', 'ai.agent.retention.checkpoint-days', '7', 'number', 'ai', 1, 1, 'LangGraph4j 检查点保留天数，超期物理删除');

-- 2. 定时清理任务（Quartz 反射调度，invokeTarget=agentDataCleanupTask.execute；sys_job 无唯一键，DELETE+INSERT 幂等）
DELETE FROM `sys_job` WHERE `job_name` = 'agent:运行数据清理' AND `job_group` = 'AI_AGENT';
INSERT INTO `sys_job` (`job_name`, `job_group`, `invoke_target`, `cron_expression`, `status`, `concurrent`, `retry_count`, `retry_interval`, `remark`) VALUES
('agent:运行数据清理', 'AI_AGENT', 'agentDataCleanupTask.execute', '0 0 3 * * ?', 1, 0, 0, 0, '按 sys_config 保留期分批物理清理 Agent run/step/todo/checkpoint');

-- 3. 手动清理按钮权限（345 已确认空闲）
DELETE FROM `sys_role_menu` WHERE `menu_id` = 345;
DELETE FROM `sys_menu` WHERE `id` = 345;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(345, '数据清理', 331, '', '', NULL, '', 5, 2, 'ai:agent:data-clean', 1, 1, 0, 0);

-- 4. 授权：拥有 Agent管理(331) 的角色授予数据清理权限(345)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 345
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 331;

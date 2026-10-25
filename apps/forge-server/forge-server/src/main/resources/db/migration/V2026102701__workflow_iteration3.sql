-- ========================================
-- 工作流迭代三：治理收尾
-- 1) 新增工作流事件审计表
-- 2) 监听器管理页下线（死表 DROP + 菜单/权限/角色授权清理）
-- 创建时间：2026-10-25
-- ========================================

-- 1. 工作流事件审计表
CREATE TABLE IF NOT EXISTS `wf_event_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型(TaskEventType/InstanceEventType枚举名)',
    `instance_id` BIGINT DEFAULT NULL COMMENT '流程实例ID',
    `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
    `task_name` VARCHAR(255) DEFAULT NULL COMMENT '任务名称',
    `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
    `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人名称',
    `summary` VARCHAR(1000) DEFAULT NULL COMMENT '事件摘要',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_instance_id` (`instance_id`),
    KEY `idx_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流事件审计';

-- 2. 监听器管理页下线
DROP TABLE IF EXISTS `wf_process_listener`;

DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 230 AND 234;
DELETE FROM `sys_menu` WHERE `id` BETWEEN 230 AND 234;

-- ========================================
-- 流程抄送记录表
-- ========================================
CREATE TABLE IF NOT EXISTS `wf_process_instance_copy` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    `start_user_id` BIGINT DEFAULT NULL COMMENT '发起人ID',
    `process_instance_name` VARCHAR(255) DEFAULT NULL COMMENT '流程实例名称',
    `process_instance_id` VARCHAR(64) DEFAULT NULL COMMENT '流程实例ID',
    `process_definition_id` VARCHAR(64) DEFAULT NULL COMMENT '流程定义ID',
    `category` VARCHAR(64) DEFAULT NULL COMMENT '流程分类',
    `activity_id` VARCHAR(64) DEFAULT NULL COMMENT '活动节点ID',
    `activity_name` VARCHAR(255) DEFAULT NULL COMMENT '活动节点名称',
    `task_id` VARCHAR(64) DEFAULT NULL COMMENT '任务ID',
    `user_id` BIGINT NOT NULL COMMENT '被抄送用户ID',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT '抄送原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程抄送记录';

-- ========================================
-- 抄送列表菜单（挂在流程管理目录下）
-- ========================================
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(240, '抄送列表', 100, '/workflow/copy', '/views/workflow/task/CopyTask', NULL, 'Message', 12, 1, 'workflow:task:list', 1, 1, 0, 0);

-- ========================================
-- 角色授权
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 240);

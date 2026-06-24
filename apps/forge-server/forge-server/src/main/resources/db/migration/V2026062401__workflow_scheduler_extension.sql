-- 工作流提醒和超时功能扩展表
-- 用于记录任务提醒和超时处理日志

-- 任务提醒记录表
CREATE TABLE IF NOT EXISTS `wf_task_remind` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `remind_type` INT NOT NULL COMMENT '提醒类型：0首次，1再次，2超时',
    `remind_time` DATETIME NOT NULL COMMENT '提醒时间',
    `remind_channel` VARCHAR(50) DEFAULT 'websocket' COMMENT '提醒渠道：websocket,email',
    `remind_content` VARCHAR(500) COMMENT '提醒内容',
    `user_ids` VARCHAR(500) COMMENT '提醒用户ID列表',
    `status` INT DEFAULT 0 COMMENT '状态：0待发送，1已发送，2失败',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_instance_id` (`instance_id`),
    INDEX `idx_remind_time` (`remind_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务提醒记录表';

-- 任务超时处理日志表
CREATE TABLE IF NOT EXISTS `wf_task_timeout_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `task_name` VARCHAR(200) COMMENT '任务名称',
    `task_key` VARCHAR(100) COMMENT '任务Key',
    `process_key` VARCHAR(100) COMMENT '流程定义Key',
    `create_time` DATETIME NOT NULL COMMENT '任务创建时间',
    `timeout_time` DATETIME NOT NULL COMMENT '超时时间',
    `timeout_minutes` INT NOT NULL COMMENT '超时时长（分钟）',
    `configured_term` INT COMMENT '配置的超时阈值（分钟）',
    `auto_action` INT COMMENT '自动处理方式：0通过，1拒绝',
    `action_time` DATETIME COMMENT '处理时间',
    `action_result` INT COMMENT '处理结果：0成功，1失败',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_instance_id` (`instance_id`),
    INDEX `idx_timeout_time` (`timeout_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务超时处理日志表';

-- 扩展 flw_task 表字段（如果不存在）
-- 注意：FlowLong 已有 remind_time、remind_repeat 字段，无需修改

-- 添加任务状态索引（优化超时查询）
-- ALTER TABLE `flw_task` ADD INDEX `idx_expire_time` (`expire_time`) IF NOT EXISTS;
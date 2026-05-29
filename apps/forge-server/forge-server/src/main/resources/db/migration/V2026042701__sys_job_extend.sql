-- ========================================
-- sys_job 扩展字段（超时控制、失败重试、执行统计）
-- ========================================

ALTER TABLE `sys_job`
    ADD COLUMN `timeout` INT DEFAULT NULL COMMENT '超时时间(秒)' AFTER `remark`,
    ADD COLUMN `retry_count` INT DEFAULT 0 COMMENT '失败重试次数' AFTER `timeout`,
    ADD COLUMN `retry_interval` INT DEFAULT 60 COMMENT '重试间隔(秒)' AFTER `retry_count`,
    ADD COLUMN `notify_config` JSON DEFAULT NULL COMMENT '通知配置' AFTER `retry_interval`,
    ADD COLUMN `job_params` JSON DEFAULT NULL COMMENT '任务参数' AFTER `notify_config`,
    ADD COLUMN `last_execute_at` DATETIME DEFAULT NULL COMMENT '最后执行时间' AFTER `job_params`,
    ADD COLUMN `last_execute_status` VARCHAR(20) DEFAULT NULL COMMENT '最后执行状态(SUCCESS/FAIL/TIMEOUT)' AFTER `last_execute_at`,
    ADD COLUMN `last_execute_duration` INT DEFAULT NULL COMMENT '最后执行耗时(毫秒)' AFTER `last_execute_status`,
    ADD COLUMN `total_execute_count` INT DEFAULT 0 COMMENT '总执行次数' AFTER `last_execute_duration`,
    ADD COLUMN `success_count` INT DEFAULT 0 COMMENT '成功次数' AFTER `total_execute_count`,
    ADD COLUMN `failure_count` INT DEFAULT 0 COMMENT '失败次数' AFTER `success_count`;

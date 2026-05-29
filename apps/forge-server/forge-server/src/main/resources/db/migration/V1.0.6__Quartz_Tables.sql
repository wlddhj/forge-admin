-- Quartz 调度器表
-- 注意: Quartz 表由 Quartz 自动创建，这里使用内存存储或手动创建

-- 任务执行日志表
CREATE TABLE IF NOT EXISTS `sys_job_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `job_id` BIGINT NOT NULL COMMENT '任务ID',
    `job_name` VARCHAR(64) NOT NULL COMMENT '任务名称',
    `job_group` VARCHAR(64) DEFAULT NULL COMMENT '任务分组',
    `invoke_target` VARCHAR(500) NOT NULL COMMENT '调用目标',
    `job_message` VARCHAR(500) DEFAULT NULL COMMENT '日志信息',
    `status` TINYINT DEFAULT 0 COMMENT '执行状态（0:失败 1:成功）',
    `exception_info` TEXT COMMENT '异常信息',
    `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration` BIGINT DEFAULT NULL COMMENT '执行耗时（毫秒）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_job_id` (`job_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';

-- 添加示例任务（如果不存在）
INSERT INTO `sys_job` (`job_name`, `job_group`, `invoke_target`, `cron_expression`, `status`, `concurrent`, `remark`, `create_time`)
SELECT '系统监控任务', 'SYSTEM', 'demoTask.execute("系统监控")', '0 0/5 * * * ?', 0, 0, '每5分钟执行一次系统监控', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `job_name` = '系统监控任务');

INSERT INTO `sys_job` (`job_name`, `job_group`, `invoke_target`, `cron_expression`, `status`, `concurrent`, `remark`, `create_time`)
SELECT '数据清理任务', 'SYSTEM', 'demoTask.cleanExpiredData()', '0 0 2 * * ?', 0, 0, '每天凌晨2点清理过期数据', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `job_name` = '数据清理任务');

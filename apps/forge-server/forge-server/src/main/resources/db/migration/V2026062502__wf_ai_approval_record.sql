-- AI审批记录表
CREATE TABLE IF NOT EXISTS `wf_ai_approval_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` bigint NOT NULL COMMENT '流程实例ID',
    `task_id` bigint NOT NULL COMMENT '任务ID',
    `task_def_key` varchar(100) DEFAULT NULL COMMENT '任务定义Key',
    `task_name` varchar(200) DEFAULT NULL COMMENT '任务名称',
    `decision` varchar(20) NOT NULL COMMENT 'AI决策结果（APPROVE/REJECT/MANUAL）',
    `confidence` int DEFAULT NULL COMMENT '置信度（0-100）',
    `reasoning` text DEFAULT NULL COMMENT 'AI分析说明',
    `raw_response` text DEFAULT NULL COMMENT '原始AI响应',
    `status` varchar(20) NOT NULL COMMENT '执行状态（SUCCESS/FAILURE/LOW_CONFIDENCE/TIMEOUT/ERROR）',
    `provider` varchar(50) DEFAULT NULL COMMENT 'AI模型提供商',
    `model_name` varchar(100) DEFAULT NULL COMMENT 'AI模型名称',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI审批记录表';
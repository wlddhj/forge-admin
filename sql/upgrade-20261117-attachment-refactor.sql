-- ========================================
-- 附件体系重构（attachment-refactor）升级 SQL —— 存量库手工执行
-- 内容：sys_attachment 补 file_config_id 列并回填 / biz_type 归一 / 附件孤儿回收 sys_job 种子(默认暂停)
-- 全新安装走 sql/init.sql（建表段已内联 tenant_id 与 file_config_id，无需本脚本）
-- 幂等：条件 DDL + WHERE 守卫，可重复执行；dev 库此前手工加过 file_config_id 列，条件 DDL 自动跳过
-- ========================================

-- 1) file_config_id 列（存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_attachment` ADD COLUMN `file_config_id` BIGINT DEFAULT NULL COMMENT ''上传时所用存储配置ID'' AFTER `storage_type`',
    'SELECT ''file_config_id 已存在,跳过''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_attachment' AND COLUMN_NAME = 'file_config_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) file_config_id 索引（存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_attachment` ADD INDEX `idx_file_config` (`file_config_id`)',
    'SELECT ''idx_file_config 已存在,跳过''')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_attachment' AND INDEX_NAME = 'idx_file_config');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 回填存量行：按 storage_type 匹配启用配置（默认配置优先，其次最小 id）
--    仅补 NULL 行，历史行若已手工回填不覆盖
UPDATE `sys_attachment` a
JOIN (
    SELECT c.`storage_type`,
           MIN(CASE WHEN c.`is_default` = 1 THEN c.`id` END) AS `def_id`,
           MIN(c.`id`) AS `any_id`
    FROM `sys_file_config` c
    WHERE c.`deleted` = 0 AND c.`status` = 1
    GROUP BY c.`storage_type`
) m ON m.`storage_type` = a.`storage_type`
SET a.`file_config_id` = COALESCE(m.`def_id`, m.`any_id`)
WHERE a.`file_config_id` IS NULL;

-- 4) biz_type 归一（统一为小写下划线，与 AttachmentBizTypes 常量对齐）
UPDATE `sys_attachment` SET `biz_type` = 'avatar' WHERE `biz_type` = 'APP_AVATAR';
UPDATE `sys_attachment` SET `biz_type` = 'brand_logo' WHERE `biz_type` = 'brand-logo';

-- 5) 附件孤儿回收任务种子（默认暂停；NOT EXISTS 防重）
INSERT INTO `sys_job` (`job_name`, `job_group`, `invoke_target`, `cron_expression`, `status`, `concurrent`, `remark`)
SELECT '附件孤儿回收', 'SYSTEM', 'attachmentCleanupTask.execute', '0 40 3 * * ?', 0, 0,
       '回收上传后未绑定的合同/审批评论/公告附件(白名单类型,7天阈值);默认暂停,观察后启用(附件模块)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `invoke_target` = 'attachmentCleanupTask.execute');

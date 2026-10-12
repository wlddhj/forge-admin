-- 附件表增加存储配置引用：记录上传时所用配置，删除物理文件时精确路由
ALTER TABLE `sys_attachment`
    ADD COLUMN `file_config_id` BIGINT NULL COMMENT '上传时所用存储配置ID' AFTER `storage_type`,
    ADD INDEX `idx_file_config` (`file_config_id`);

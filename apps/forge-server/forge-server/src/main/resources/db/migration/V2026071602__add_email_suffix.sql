-- 邮箱搜索支持：新增 email_suffix 字段（明文邮箱，用于 LIKE 后缀搜索）
-- 配合 phone_suffix (后 4 位) 实现加密字段的可搜索
ALTER TABLE `sys_user` ADD COLUMN `email_suffix` VARCHAR(100) DEFAULT NULL COMMENT '邮箱（明文，便于搜索）' AFTER `phone_suffix`;
ALTER TABLE `sys_user` ADD INDEX `idx_email_suffix` (`email_suffix`);
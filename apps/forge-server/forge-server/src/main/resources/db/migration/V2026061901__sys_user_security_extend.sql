-- V2026061901__sys_user_security_extend.sql
-- 等保二级改造：sys_user 加安全字段 + 新建密码历史表

ALTER TABLE `sys_user`
    ADD COLUMN `password_update_time` DATETIME DEFAULT NULL COMMENT '密码最后修改时间' AFTER `last_login_ip`,
    ADD COLUMN `first_login` TINYINT NOT NULL DEFAULT 0 COMMENT '是否首次登录需强制改密(0:否 1:是)' AFTER `password_update_time`,
    ADD COLUMN `password_error_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数' AFTER `first_login`,
    ADD COLUMN `lock_time` DATETIME DEFAULT NULL COMMENT '账号锁定截止时间' AFTER `password_error_count`,
    ADD COLUMN `phone_suffix` VARCHAR(4) DEFAULT NULL COMMENT '手机号后4位（明文，便于精确查询）' AFTER `lock_time`,
    ADD INDEX `idx_lock_time` (`lock_time`);

-- 现有用户标记 password_update_time = NOW()，避免立即触发密码过期
UPDATE `sys_user` SET `password_update_time` = NOW() WHERE `password_update_time` IS NULL;

CREATE TABLE `sys_user_password_history` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id_create_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户密码历史';

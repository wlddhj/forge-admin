-- ============================================================
-- 二级等保改造：手动迁移 SQL（幂等版本）
-- 适用于：sys_user 表结构扩展 + 密码历史表
-- 可重复执行，已存在的字段/表会被跳过
-- ============================================================

-- 数据库名（按需修改）
-- USE forge_admin;

-- ---------- 1. sys_user 表添加安全字段（幂等） ----------

-- 1.1 password_update_time
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password_update_time');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `password_update_time` DATETIME DEFAULT NULL COMMENT ''密码最后修改时间'' AFTER `last_login_ip`',
    'SELECT ''password_update_time already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 first_login
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'first_login');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `first_login` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否首次登录需强制改密(0:否 1:是)'' AFTER `password_update_time`',
    'SELECT ''first_login already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 password_error_count
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password_error_count');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `password_error_count` INT NOT NULL DEFAULT 0 COMMENT ''连续登录失败次数'' AFTER `first_login`',
    'SELECT ''password_error_count already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 lock_time
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'lock_time');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `lock_time` DATETIME DEFAULT NULL COMMENT ''账号锁定截止时间'' AFTER `password_error_count`',
    'SELECT ''lock_time already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.5 phone_suffix
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'phone_suffix');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `phone_suffix` VARCHAR(4) DEFAULT NULL COMMENT ''手机号后4位（明文，便于精确查询）'' AFTER `lock_time`',
    'SELECT ''phone_suffix already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.6 idx_lock_time 索引
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_lock_time');
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE `sys_user` ADD INDEX `idx_lock_time` (`lock_time`)',
    'SELECT ''idx_lock_time already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 现有用户密码更新时间初始化 ----------
UPDATE `sys_user` SET `password_update_time` = NOW() WHERE `password_update_time` IS NULL;

-- ---------- 3. 创建密码历史表（幂等） ----------
CREATE TABLE IF NOT EXISTS `sys_user_password_history` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id_create_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户密码历史';

-- ---------- 4. 验证 ----------
SELECT '=== sys_user 安全字段 ===' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
  AND COLUMN_NAME IN ('password_update_time','first_login','password_error_count','lock_time','phone_suffix');

SELECT '=== 密码历史表 ===' AS info;
SHOW TABLES LIKE 'sys_user_password_history';

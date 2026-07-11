-- 给 sys_user 加 tenant_id 列
-- MySQL 8.0.29+ 支持 ADD COLUMN IF NOT EXISTS
-- 对于 MySQL < 8.0.29，需要使用以下替代逻辑：
-- SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
--                    WHERE TABLE_SCHEMA = DATABASE()
--                      AND TABLE_NAME = 'sys_user'
--                      AND COLUMN_NAME = 'tenant_id');
-- SET @sql = IF(@col_exists = 0,
--               'ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT "租户ID" AFTER id',
--               'SELECT 1');
-- PREPARE stmt FROM @sql;
-- EXECUTE stmt;
-- DEALLOCATE PREPARE stmt;
ALTER TABLE sys_user
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;

-- 删除原 username 唯一索引
ALTER TABLE sys_user DROP INDEX IF EXISTS uk_username;

-- 加联合唯一索引 (tenant_id, username)
ALTER TABLE sys_user ADD UNIQUE INDEX uk_tenant_username (tenant_id, username);

-- 加 tenant_id 索引（提升按租户查询性能）
CREATE INDEX idx_tenant ON sys_user (tenant_id);

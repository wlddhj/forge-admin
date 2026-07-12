-- =============================================================================
-- app_user 表加 tenant_id 字段，并回填默认租户
-- =============================================================================
-- 兼容性：MySQL 5.7 / 8.0 < 8.0.29 / MariaDB 10.2+ 均可执行
-- 重跑：如列已存在会报 "Duplicate column"，可忽略
--       使用 mysql --force 选项可继续执行后续语句
-- =============================================================================

-- app_user 加 tenant_id 列
ALTER TABLE app_user
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

-- 历史数据回填（仅限无租户或租户为0的记录）
UPDATE app_user SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

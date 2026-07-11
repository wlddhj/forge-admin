-- =============================================================================
-- app_user 表加 tenant_id 字段，并回填默认租户
-- =============================================================================

-- app_user 加 tenant_id 列
ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

-- 历史数据回填（仅限无租户或租户为0的记录）
UPDATE app_user SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

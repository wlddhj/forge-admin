-- =============================================================================
-- Task 14: 业务表统一加 tenant_id 列与索引
-- =============================================================================
-- 扫描命令: grep -r "@TableName" forge-module-*/forge-module-*-api/src/main/java/com/forge/modules/*/entity/
--
-- 共享表（不加 tenant_id）:
--   sys_menu, sys_dict_data, sys_dict_type, sys_config, sys_file_config,
--   sys_job, sys_role, sys_role_menu, sys_tenant, sys_user, app_user
--
-- 关联表（加 tenant_id）:
--   sys_user_role, sys_user_position, sys_role_dept — 关联租户内的用户/角色/部门
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 系统模块表
-- ---------------------------------------------------------------------------

ALTER TABLE sys_notice
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_attachment
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_login_log
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_operation_log
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_position
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_dept
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_key_sequence
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_user_password_history
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

-- 关联表（租户内用户-角色/用户-岗位/角色-部门）
ALTER TABLE sys_user_role
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_user_position
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_role_dept
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

-- ---------------------------------------------------------------------------
-- 工作流模块表
-- ---------------------------------------------------------------------------

ALTER TABLE wf_category
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE wf_form
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE wf_process_ext
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE wf_process_expression
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE wf_process_listener
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE wf_approval_comment
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE wf_ai_approval_record
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

-- ---------------------------------------------------------------------------
-- 大屏模块表
-- ---------------------------------------------------------------------------

ALTER TABLE sys_screen
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_screen_data_source
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_screen_sql_whitelist
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_screen_role
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

-- ---------------------------------------------------------------------------
-- AI 模块表
-- ---------------------------------------------------------------------------

ALTER TABLE ai_chat_conversation
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE ai_chat_message
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE ai_document
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE ai_api_usage
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE ai_model_config
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

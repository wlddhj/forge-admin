-- =============================================================================
-- Task 14: 业务表统一加 tenant_id 列与索引
-- =============================================================================
-- 扫描命令: grep -r "@TableName" forge-module-*/forge-module-*-api/src/main/java/com/forge/modules/*/entity/
--
-- 兼容性：MySQL 5.7 / 8.0 < 8.0.29 / MariaDB 10.2+ 均可执行
-- 重跑：如某表已存在 tenant_id 列会报 "Duplicate column"，可忽略
--       使用 mysql --force 选项可继续执行后续语句：
--         mysql -u root -p --force forge_admin < V2026071103__add_tenant_id_to_business_tables.sql
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
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_attachment
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_login_log
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_operation_log
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_position
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_dept
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_key_sequence
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_user_password_history
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

-- 关联表（租户内用户-角色/用户-岗位/角色-部门）
ALTER TABLE sys_user_role
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_user_position
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_role_dept
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

-- ---------------------------------------------------------------------------
-- 工作流模块表
-- ---------------------------------------------------------------------------

ALTER TABLE wf_category
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE wf_form
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE wf_process_ext
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE wf_process_expression
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE wf_process_listener
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE wf_approval_comment
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE wf_ai_approval_record
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

-- ---------------------------------------------------------------------------
-- 大屏模块表
-- ---------------------------------------------------------------------------

ALTER TABLE sys_screen
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_screen_data_source
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_screen_sql_whitelist
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE sys_screen_role
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

-- ---------------------------------------------------------------------------
-- AI 模块表
-- ---------------------------------------------------------------------------

ALTER TABLE ai_chat_conversation
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE ai_chat_message
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE ai_document
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE ai_api_usage
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

ALTER TABLE ai_model_config
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX idx_tenant (tenant_id);

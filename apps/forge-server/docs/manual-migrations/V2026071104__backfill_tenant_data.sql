-- =============================================================================
-- 回填租户数据：将所有历史业务数据归到默认租户（tenant_id = 1）
-- 前提：sys_tenant(id=1) 和 sys_tenant_package(id=1) 已存在
-- =============================================================================

-- 系统模块业务表
UPDATE sys_notice SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_attachment SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_login_log SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_operation_log SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_position SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_dept SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_key_sequence SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_user_password_history SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_user_role SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_user_position SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_role_dept SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

-- 工作流模块业务表
UPDATE wf_category SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE wf_form SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE wf_process_ext SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE wf_process_expression SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE wf_process_listener SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE wf_approval_comment SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE wf_ai_approval_record SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

-- 大屏模块业务表
UPDATE sys_screen SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_screen_data_source SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_screen_sql_whitelist SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_screen_role SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

-- AI 模块业务表
UPDATE ai_chat_conversation SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE ai_chat_message SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE ai_document SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE ai_api_usage SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE ai_model_config SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

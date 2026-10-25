-- ============================================================
-- 租户全量租户化(路径A) | 2026-10-25
-- 背景:审计修正后真实缺口 16 表缺 tenant_id 列(init.sql 尾部
--       多租户段已覆盖 13 张系统表,AI 4 表与 flw_ 引擎表此前已治理)。
--       TENANT_ENABLE=true 部署下这些表读写全部 SQL 报错。
-- 治理:
--   A组(9表) 纯加列+索引:oauth2 授权两表、wf_ 扩展6表、sys_screen_role
--   B组(6表) 加列+唯一键联合化:key_sequence/social_user/screen/
--           screen_data_source/screen_sql_whitelist/wf_category
--   C组(1表) 加列+全局唯一键保留:oauth2_registered_client
--           (client_id 是 OAuth2 协议全局标识;tenant_id 仅作归属)
-- 存量数据:DEFAULT 1 全部归属默认租户(id=1, code='default')。
-- 注意:
--   1. MySQL ALTER 不支持 IF NOT EXISTS,本脚本仅执行一次;
--      重复执行报 Duplicate column/index 可忽略。
--   2. oauth2_authorization 为大表,生产环境建议低峰执行或 pt-osc。
-- ============================================================

-- ---------- A组:纯加列+索引 ----------

ALTER TABLE `oauth2_authorization`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `oauth2_authorization_consent`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `wf_form`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `wf_process_ext`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `wf_process_expression`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `wf_process_listener`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `wf_approval_comment`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `wf_ai_approval_record`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

ALTER TABLE `sys_screen_role`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

-- ---------- B组:加列+唯一键联合化 ----------

-- 发号器:跨租户独立发号(两租户同 key_category 各自递增)
ALTER TABLE `sys_key_sequence`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_key_sequence`
  DROP INDEX `uk_key_category`,
  ADD UNIQUE INDEX `uk_tenant_key_category` (`tenant_id`, `key_category`);

-- 三方账号:允许各租户共用同一微信/钉钉应用(同 openid 绑定不同租户账号)
ALTER TABLE `sys_social_user`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_social_user`
  DROP INDEX `uk_source_openid`,
  ADD UNIQUE INDEX `uk_tenant_source_openid` (`tenant_id`, `source`, `open_id`);

-- 大屏:渲染走登录态 API(非匿名),code 租户内唯一即可
ALTER TABLE `sys_screen`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_screen`
  DROP INDEX `uk_screen_code`,
  ADD UNIQUE INDEX `uk_tenant_screen_code` (`tenant_id`, `code`);

ALTER TABLE `sys_screen_data_source`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_screen_data_source`
  DROP INDEX `uk_screen_ds_code`,
  ADD UNIQUE INDEX `uk_tenant_screen_ds_code` (`tenant_id`, `code`);

ALTER TABLE `sys_screen_sql_whitelist`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_screen_sql_whitelist`
  DROP INDEX `uk_whitelist_table`,
  ADD UNIQUE INDEX `uk_tenant_whitelist_table` (`tenant_id`, `schema_name`, `table_name`);

-- 流程分类:租户内唯一
ALTER TABLE `wf_category`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `wf_category`
  DROP INDEX `uk_category_code`,
  ADD UNIQUE INDEX `uk_tenant_category_code` (`tenant_id`, `category_code`);

-- ---------- C组:加列+全局唯一键保留 ----------

ALTER TABLE `oauth2_registered_client`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
-- uk_client_id 保留全局唯一:OAuth2 授权请求按 client_id 定位(无租户上下文)

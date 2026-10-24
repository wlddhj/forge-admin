-- ============================================================
-- AI 模块租户一致性治理 | 2026-10-24
-- 背景:ai_chat_conversation/ai_chat_message/ai_document/ai_api_usage
--       缺 tenant_id 列,TENANT_ENABLE=true 部署下读写全部 SQL 报错。
-- 治理:4 表补列(存量行自动归属默认租户 1);ai_model_config 进租户
--       忽略清单(application.yml,平台统一配置共享语义)。
-- 注意:MySQL ALTER 不支持 IF NOT EXISTS,本脚本仅执行一次;
--       重复执行报 Duplicate column 可忽略。
-- ============================================================

ALTER TABLE `ai_chat_conversation`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `user_id`;

ALTER TABLE `ai_chat_message`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `conversation_id`;

ALTER TABLE `ai_document`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `user_id`;

ALTER TABLE `ai_api_usage`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `user_id`;

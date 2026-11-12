-- ============================================================
-- 工作流事件审计表租户化 | 2026-11-12
-- 背景:多租户开闸前置(评审修复第二批)——wf_event_audit 建表时漏加
--       tenant_id 列,TENANT_ENABLE=true 部署下该表写入全部 SQL 报错
--       (Unknown column 'tenant_id'):AuditLogEventHandler 事件落库与
--       ReliableEventConsumer 终败审计两条链路均中断。
-- 治理:纯加列+索引(照 upgrade-20261025-tenant-full.sql A组口径)。
-- 存量数据:DEFAULT 1 全部归属默认租户(id=1, code='default')。
-- 注意:
--   1. MySQL ALTER 不支持 IF NOT EXISTS,本脚本仅执行一次;
--      重复执行报 Duplicate column/index 可忽略。
--   2. dev 库无 Flyway,须手工执行。
-- ============================================================

-- ---------- A组:纯加列+索引 ----------

ALTER TABLE `wf_event_audit`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

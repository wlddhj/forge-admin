-- ============================================================
-- 升级SQL：FlowLong 引擎 1.2.5 → 1.2.7 DDL 迁移
-- 日期：2026-10-27
-- 说明：v1.2.7 在任务两表新增紧急程度列（上游 db/flowlong-mysql.sql diff 确认仅此变更）。
--       列为 NOT NULL DEFAULT 0，不加列则新引擎 insert 直接报 Unknown column。
-- 注意：①dev 库未启用 Flyway，手工执行；②ALTER 不含 IF NOT EXISTS（MySQL 8 不支持），
--       切勿重复执行；③重复执行报 Duplicate column 属预期，忽略即可。
-- ============================================================

USE forge_admin;

ALTER TABLE `flw_task`
    ADD COLUMN `urgent` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '任务紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要';

ALTER TABLE `flw_his_task`
    ADD COLUMN `urgent` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '任务紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要';

-- 补充：FlwInstance/FlwHisInstance 实体含 urgent 字段（1.2.7 实例紧急程度），
-- 上游 db 脚本遗漏未同步，缺失将导致实例 SELECT/INSERT 报 Unknown column
ALTER TABLE `flw_instance`
    ADD COLUMN `urgent` TINYINT DEFAULT 0 COMMENT '实例紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要';

ALTER TABLE `flw_his_instance`
    ADD COLUMN `urgent` TINYINT DEFAULT 0 COMMENT '实例紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要';

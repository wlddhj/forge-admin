-- ========================================
-- flw_ 流程引擎表 tenant_id 存量清洗与默认值补齐
-- 背景：FlowLong 实体 tenantId 由 FlowCreator 传导，历史版本未设置，
--       且 flw_ 表 tenant_id 列无 DEFAULT，导致落 NULL。
-- 说明：保持 VARCHAR(50) 类型不变（拦截器注入字符串字面量，精确匹配），
--       仅补 DEFAULT '1'（与 sys 表 tenant_id DEFAULT 1 约定对齐）。
-- 幂等：UPDATE 条件不命中则零行；ALTER MODIFY 为幂等 DDL。
-- ========================================

-- 1. 存量清洗：NULL 或非数字租户值统一置 '1'（非数字值理论上不存在，实证全 NULL；显式条件防止静默丢弃）
-- 2. 补默认值：INSERT 不含 tenant_id 列时回落 '1'

UPDATE `flw_process`        SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';
UPDATE `flw_instance`       SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';
UPDATE `flw_his_instance`   SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';
UPDATE `flw_task`           SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';
UPDATE `flw_his_task`       SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';
UPDATE `flw_task_actor`     SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';
UPDATE `flw_his_task_actor` SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';
UPDATE `flw_ext_instance`   SET `tenant_id` = '1' WHERE `tenant_id` IS NULL OR `tenant_id` NOT REGEXP '^[0-9]+$';

ALTER TABLE `flw_process`        MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';
ALTER TABLE `flw_instance`       MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';
ALTER TABLE `flw_his_instance`   MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';
ALTER TABLE `flw_task`           MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';
ALTER TABLE `flw_his_task`       MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';
ALTER TABLE `flw_task_actor`     MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';
ALTER TABLE `flw_his_task_actor` MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';
ALTER TABLE `flw_ext_instance`   MODIFY COLUMN `tenant_id` VARCHAR(50) DEFAULT '1' COMMENT '租户ID';

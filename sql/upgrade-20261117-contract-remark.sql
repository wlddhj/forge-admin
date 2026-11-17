-- ========================================
-- 合同备注字段——ct_contract 补 remark
-- 管理性信息：任意状态可改（含审批中/已定稿），不参与渲染与定稿快照
-- 存量库升级：幂等可重跑；全新库 init-contract.sql 已含
-- ========================================

DELIMITER $$
DROP PROCEDURE IF EXISTS `upgrade_20261117_ct_contract_remark` $$
CREATE PROCEDURE `upgrade_20261117_ct_contract_remark`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ct_contract'
                     AND COLUMN_NAME = 'remark') THEN
        ALTER TABLE `ct_contract`
            ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL
                COMMENT '备注（管理性信息，任意状态可改，不参与渲染与定稿快照）' AFTER `process_no`;
    END IF;
END $$
DELIMITER ;
CALL `upgrade_20261117_ct_contract_remark`();
DROP PROCEDURE IF EXISTS `upgrade_20261117_ct_contract_remark`;

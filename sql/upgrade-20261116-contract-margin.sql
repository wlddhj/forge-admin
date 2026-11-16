-- ========================================
-- 合同模板页边距档位（窄/正常/宽，Word 同口径）——ct_template 补 margin_preset
-- 存量库升级：幂等可重跑；全新库 init-contract.sql 已含
-- ========================================

DELIMITER $$
DROP PROCEDURE IF EXISTS `upgrade_20261116_ct_margin_preset` $$
CREATE PROCEDURE `upgrade_20261116_ct_margin_preset`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ct_template'
                     AND COLUMN_NAME = 'margin_preset') THEN
        ALTER TABLE `ct_template`
            ADD COLUMN `margin_preset` VARCHAR(8) NOT NULL DEFAULT 'NORMAL'
                COMMENT '页边距档位（NARROW:窄 NORMAL:正常 WIDE:宽）' AFTER `paper_orientation`;
    END IF;
END $$
DELIMITER ;
CALL `upgrade_20261116_ct_margin_preset`();
DROP PROCEDURE IF EXISTS `upgrade_20261116_ct_margin_preset`;

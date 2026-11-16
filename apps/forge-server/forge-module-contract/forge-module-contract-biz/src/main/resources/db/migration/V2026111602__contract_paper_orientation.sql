-- ========================================
-- 合同模板纸张方向（横/竖）——ct_template 补 paper_orientation
-- 合同模板纸张方向增量归档（内容与 sql/upgrade-20261116-contract-orientation.sql 一致）
-- ========================================

DELIMITER $$
DROP PROCEDURE IF EXISTS `upgrade_20261116_ct_paper_orientation` $$
CREATE PROCEDURE `upgrade_20261116_ct_paper_orientation`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ct_template'
                     AND COLUMN_NAME = 'paper_orientation') THEN
        ALTER TABLE `ct_template`
            ADD COLUMN `paper_orientation` VARCHAR(8) NOT NULL DEFAULT 'PORTRAIT'
                COMMENT '纸张方向（PORTRAIT:纵向 LANDSCAPE:横向）' AFTER `paper_size`;
    END IF;
END $$
DELIMITER ;
CALL `upgrade_20261116_ct_paper_orientation`();
DROP PROCEDURE IF EXISTS `upgrade_20261116_ct_paper_orientation`;

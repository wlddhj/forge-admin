-- ========================================
-- 合同分类树形化（contract-category-tree）
-- 存量库升级用：幂等可重跑；全新库走 sql/init-contract.sql（树版）
-- 分类字典 contract_category → ct_category 两级树（叶子 code 沿用字典 value，平滑改挂）
-- 菜单 id 段 326-330（2026-11-16 复核：324/325 被 AI 模型按钮占用，326-330 空闲）
-- ========================================

-- ① 合同分类树表（照抄 wf_category 范式；唯一索引含 deleted 按 A1 规范）
CREATE TABLE IF NOT EXISTS `ct_category` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `category_code` VARCHAR(100) NOT NULL COMMENT '分类编码（租户内唯一；叶子编码参与合同编号）',
    `parent_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID（0=根）',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（0:停用 1:启用）',
    `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_by`     BIGINT       DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     BIGINT       DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除墓碑（delval=id）',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID（多租户拦截器自动隔离）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `category_code`, `deleted`) COMMENT '租户内编码唯一',
    KEY `idx_parent` (`parent_id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同分类（树形）';

-- ② 种子：默认租户两级树（仅当 ct_category 空时播种；分组可页面调整）
--    人事类 RS（劳动/保密）｜商务类 BS（采购/销售/服务）｜财务类 FN（借款/租赁）
--    注：MySQL 不允许 INSERT...SELECT 的子查询查目标表，判空放存储过程变量
DELIMITER $$
DROP PROCEDURE IF EXISTS `upgrade_20261117_ct_category_seed` $$
CREATE PROCEDURE `upgrade_20261117_ct_category_seed`()
BEGIN
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_rs BIGINT;
    DECLARE v_bs BIGINT;
    DECLARE v_fn BIGINT;
    SELECT COUNT(*) INTO v_count FROM `ct_category`;
    IF v_count = 0 THEN
        INSERT INTO `ct_category` (`category_name`, `category_code`, `parent_id`, `sort_order`, `status`, `tenant_id`, `create_by`)
        VALUES ('人事类', 'RS', 0, 1, 1, 1, 1);
        SET v_rs = LAST_INSERT_ID();
        INSERT INTO `ct_category` (`category_name`, `category_code`, `parent_id`, `sort_order`, `status`, `tenant_id`, `create_by`)
        VALUES ('商务类', 'BS', 0, 2, 1, 1, 1);
        SET v_bs = LAST_INSERT_ID();
        INSERT INTO `ct_category` (`category_name`, `category_code`, `parent_id`, `sort_order`, `status`, `tenant_id`, `create_by`)
        VALUES ('财务类', 'FN', 0, 3, 1, 1, 1);
        SET v_fn = LAST_INSERT_ID();
        INSERT INTO `ct_category` (`category_name`, `category_code`, `parent_id`, `sort_order`, `status`, `tenant_id`, `create_by`) VALUES
        ('劳动合同', 'LD', v_rs, 1, 1, 1, 1),
        ('保密协议', 'BM', v_rs, 2, 1, 1, 1),
        ('采购合同', 'CG', v_bs, 1, 1, 1, 1),
        ('销售合同', 'XS', v_bs, 2, 1, 1, 1),
        ('服务合同', 'FW', v_bs, 3, 1, 1, 1),
        ('借款合同', 'JK', v_fn, 1, 1, 1, 1),
        ('租赁合同', 'ZL', v_fn, 2, 1, 1, 1);
    END IF;
END $$
DELIMITER ;
CALL `upgrade_20261117_ct_category_seed`();
DROP PROCEDURE IF EXISTS `upgrade_20261117_ct_category_seed`;

-- ③ ct_template 补 category_id 列（幂等判列）
DELIMITER $$
DROP PROCEDURE IF EXISTS `upgrade_20261117_ct_category_migration` $$
CREATE PROCEDURE `upgrade_20261117_ct_category_migration`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ct_template'
                     AND COLUMN_NAME = 'category_id') THEN
        ALTER TABLE `ct_template` ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT '合同分类ID（ct_category，仅启用叶子）' AFTER `category_code`;
    END IF;
    -- 存量模板按旧编码改挂对应叶子 → 迁移完成移除旧编码列（同分支保证重跑安全；【假设】dev 阶段无生产包袱）
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'ct_template'
                 AND COLUMN_NAME = 'category_code') THEN
        UPDATE `ct_template` t
          JOIN `ct_category` c ON c.`category_code` = t.`category_code`
                              AND c.`tenant_id` = t.`tenant_id` AND c.`deleted` = 0
           SET t.`category_id` = c.`id`
         WHERE t.`category_id` IS NULL AND t.`deleted` = 0;
        ALTER TABLE `ct_template` DROP COLUMN `category_code`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ct_template'
                     AND INDEX_NAME = 'idx_category') THEN
        ALTER TABLE `ct_template` ADD INDEX `idx_category` (`category_id`);
    END IF;
END $$
DELIMITER ;
CALL `upgrade_20261117_ct_category_migration`();
DROP PROCEDURE IF EXISTS `upgrade_20261117_ct_category_migration`;

-- ④ 原分类字典停用（不删，防回滚；页面全部改走树）
UPDATE `sys_dict_type` SET `status` = 0 WHERE `dict_type` = 'contract_category';
UPDATE `sys_dict_data` SET `status` = 0 WHERE `dict_type` = 'contract_category';

-- ⑤ 菜单：合同管理(285) 下新增「合同分类」（id 326-330，sort_order=0 排最前）
INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(326, '合同分类', 285, '/contract/category', '/views/contract/category/index', NULL, 'FolderOpened', 0, 1, 'contract:category:list', 1, 1, 0, 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(327, '分类新增', 326, '', '', NULL, '', 1, 2, 'contract:category:create', 1, 1, 0, 0),
(328, '分类修改', 326, '', '', NULL, '', 2, 2, 'contract:category:update', 1, 1, 0, 0),
(329, '分类删除', 326, '', '', NULL, '', 3, 2, 'contract:category:delete', 1, 1, 0, 0),
(330, '分类停启用', 326, '', '', NULL, '', 4, 2, 'contract:category:update', 1, 1, 0, 0);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 326), (1, 327), (1, 328), (1, 329), (1, 330);

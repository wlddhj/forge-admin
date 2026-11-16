-- ========================================
-- 合同模板模块 forge-module-contract（全新库一次性初始化·树形分类版）
-- DROP+CREATE 可重复执行；存量库增量走 upgrade-20261116-contract.sql + upgrade-20261117-contract-category.sql
-- 菜单 id 段：285-299（模块首批）+ 326-330（分类）
-- 分类为 ct_category 两级树（叶子编码参与合同编号），原 contract_category 字典方案已废弃
-- ========================================

-- 合同分类树表
DROP TABLE IF EXISTS `ct_category`;
CREATE TABLE `ct_category` (
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

-- 种子：默认租户两级树（人事 RS / 商务 BS / 财务 FN；分组可页面调整）
INSERT INTO `ct_category` (`id`, `category_name`, `category_code`, `parent_id`, `sort_order`, `status`, `tenant_id`, `create_by`) VALUES
(1, '人事类', 'RS', 0, 1, 1, 1, 1),
(2, '商务类', 'BS', 0, 2, 1, 1, 1),
(3, '财务类', 'FN', 0, 3, 1, 1, 1),
(4, '劳动合同', 'LD', 1, 1, 1, 1, 1),
(5, '保密协议', 'BM', 1, 2, 1, 1, 1),
(6, '采购合同', 'CG', 2, 1, 1, 1, 1),
(7, '销售合同', 'XS', 2, 2, 1, 1, 1),
(8, '服务合同', 'FW', 2, 3, 1, 1, 1),
(9, '借款合同', 'JK', 3, 1, 1, 1, 1),
(10, '租赁合同', 'ZL', 3, 2, 1, 1, 1);

-- 合同模板主表（category_id 关联 ct_category 叶子）
DROP TABLE IF EXISTS `ct_template`;
CREATE TABLE `ct_template` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id`        BIGINT       NOT NULL COMMENT '合同分类ID（ct_category，仅启用叶子）',
    `name`               VARCHAR(100) NOT NULL COMMENT '模板名称',
    `current_version_id` BIGINT       DEFAULT NULL COMMENT '当前版本ID（首版保存后回填，合同创建锁定该版本）',
    `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态（0:草稿 1:启用 2:停用）',
    `paper_size`         VARCHAR(16)  NOT NULL DEFAULT 'A4' COMMENT '纸张（A4/A5/LETTER/LEGAL/CUSTOM）',
    `custom_width_mm`    DECIMAL(6,1) DEFAULT NULL COMMENT '自定义纸宽 mm（CUSTOM 时有效）',
    `custom_height_mm`   DECIMAL(6,1) DEFAULT NULL COMMENT '自定义纸高 mm（CUSTOM 时有效）',
    `remark`             VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_by`          BIGINT       DEFAULT NULL COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          BIGINT       DEFAULT NULL COMMENT '更新人',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除墓碑（delval=id）',
    `tenant_id`          BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID（多租户拦截器自动隔离）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`) COMMENT '租户内模板名唯一',
    KEY `idx_category` (`category_id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同模板';

-- 合同模板版本表（content 即版本内容，可追溯可回滚）
DROP TABLE IF EXISTS `ct_template_version`;
CREATE TABLE `ct_template_version` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_id`     BIGINT      NOT NULL COMMENT '模板ID',
    `version_no`      INT         NOT NULL DEFAULT 1 COMMENT '版本号（模板内自增）',
    `content_html`    MEDIUMTEXT  COMMENT '模板内容 HTML（含 data-field 变量芯片）',
    `field_defs_json` MEDIUMTEXT  COMMENT '变量定义 JSON（key/label/type/required/options）',
    `change_log`      VARCHAR(500) DEFAULT NULL COMMENT '版本变更说明',
    `create_by`       BIGINT      DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT      DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         BIGINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除墓碑（delval=id）',
    `tenant_id`       BIGINT      NOT NULL DEFAULT 1 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_ver` (`template_id`, `version_no`, `deleted`) COMMENT '模板内版本号唯一',
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同模板版本';

-- 合同记录表
DROP TABLE IF EXISTS `ct_contract`;
CREATE TABLE `ct_contract` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `contract_no`        VARCHAR(64) DEFAULT NULL COMMENT '合同编号（终态通过时生成 CT-<分类码>-YYYYMMDD-XXXX）',
    `title`              VARCHAR(200) NOT NULL COMMENT '合同标题',
    `template_id`        BIGINT      NOT NULL COMMENT '模板ID',
    `template_version_id` BIGINT     NOT NULL COMMENT '模板版本ID（创建时锁定）',
    `field_values_json`  MEDIUMTEXT  COMMENT '变量值 JSON（key→值）',
    `status`             TINYINT     NOT NULL DEFAULT 0 COMMENT '状态（0:草稿 1:审批中 2:已通过 3:已驳回 4:已撤销；3/4 可重新编辑回 0）',
    `rendered_html`      MEDIUMTEXT  COMMENT '定稿渲染快照（终态通过时冻结，模板后续改版不影响）',
    `pdf_attachment_id`  BIGINT      DEFAULT NULL COMMENT '定稿水印 PDF 附件ID（sys_attachment）',
    `process_instance_id` BIGINT     DEFAULT NULL COMMENT '流程实例ID（businessKey=contract:{id}）',
    `process_no`         VARCHAR(64) DEFAULT NULL COMMENT '流程编号（发起回写）',
    `create_by`          BIGINT      DEFAULT NULL COMMENT '创建人',
    `create_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          BIGINT      DEFAULT NULL COMMENT '更新人',
    `update_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            BIGINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除墓碑（delval=id）',
    `tenant_id`          BIGINT      NOT NULL DEFAULT 1 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_no` (`tenant_id`, `contract_no`, `deleted`) COMMENT '租户内编号唯一（NULL 可重复）',
    KEY `idx_template` (`template_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同记录';

-- ========================================
-- 菜单与权限（id 285-299 模块首批 + 326-330 分类，超管授权）
-- ========================================
INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(285, '合同管理', 0, '/contract', 'Layout', '/contract/category', 'Folder', 10, 0, NULL, 1, 1, 0, 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(326, '合同分类', 285, '/contract/category', '/views/contract/category/index', NULL, 'FolderOpened', 0, 1, 'contract:category:list', 1, 1, 0, 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(327, '分类新增', 326, '', '', NULL, '', 1, 2, 'contract:category:create', 1, 1, 0, 0),
(328, '分类修改', 326, '', '', NULL, '', 2, 2, 'contract:category:update', 1, 1, 0, 0),
(329, '分类删除', 326, '', '', NULL, '', 3, 2, 'contract:category:delete', 1, 1, 0, 0),
(330, '分类停启用', 326, '', '', NULL, '', 4, 2, 'contract:category:update', 1, 1, 0, 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(286, '合同模板', 285, '/contract/template', '/views/contract/template/index', NULL, 'DocumentCopy', 1, 1, 'contract:template:list', 1, 1, 0, 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(287, '模板新增', 286, '', '', NULL, '', 1, 2, 'contract:template:create', 1, 1, 0, 0),
(288, '模板修改', 286, '', '', NULL, '', 2, 2, 'contract:template:update', 1, 1, 0, 0),
(289, '模板删除', 286, '', '', NULL, '', 3, 2, 'contract:template:delete', 1, 1, 0, 0),
(290, '模板启停', 286, '', '', NULL, '', 4, 2, 'contract:template:status', 1, 1, 0, 0),
(291, '模板版本', 286, '', '', NULL, '', 5, 2, 'contract:template:version', 1, 1, 0, 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(292, '合同列表', 285, '/contract/contract', '/views/contract/contract/index', NULL, 'Tickets', 2, 1, 'contract:contract:list', 1, 1, 0, 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(293, '合同新增', 292, '', '', NULL, '', 1, 2, 'contract:contract:create', 1, 1, 0, 0),
(294, '合同修改', 292, '', '', NULL, '', 2, 2, 'contract:contract:update', 1, 1, 0, 0),
(295, '合同删除', 292, '', '', NULL, '', 3, 2, 'contract:contract:delete', 1, 1, 0, 0),
(296, '合同预览', 292, '', '', NULL, '', 4, 2, 'contract:contract:preview', 1, 1, 0, 0),
(297, '合同提交', 292, '', '', NULL, '', 5, 2, 'contract:contract:submit', 1, 1, 0, 0),
(298, '合同打印', 292, '', '', NULL, '', 6, 2, 'contract:contract:print', 1, 1, 0, 0),
(299, '合同导出', 292, '', '', NULL, '', 7, 2, 'contract:contract:export', 1, 1, 0, 0);

-- 超管角色授权（role_id=1）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 285), (1, 286), (1, 287), (1, 288), (1, 289), (1, 290), (1, 291),
(1, 292), (1, 293), (1, 294), (1, 295), (1, 296), (1, 297), (1, 298), (1, 299),
(1, 326), (1, 327), (1, 328), (1, 329), (1, 330);

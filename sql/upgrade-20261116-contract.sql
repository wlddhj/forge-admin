-- ========================================
-- 合同模板模块 forge-module-contract
-- 存量库升级用：幂等可重复执行；全新库走 sql/init-contract.sql
-- 菜单 id 段 285-299（demo 占 280-284、AI 占 300-323，2026-11-16 核对空闲）
-- 字典 id 段：type 225 / data 2250-2256（现值 max 224/2245，核对空闲）
-- ========================================

-- 合同模板主表（唯一索引含 deleted，按编码规范 v1.1：deleted BIGINT + delval=id）
CREATE TABLE IF NOT EXISTS `ct_template` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_code`      VARCHAR(32)  NOT NULL COMMENT '分类编码（contract_category 字典 value）',
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
    KEY `idx_category` (`category_code`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同模板';

-- 合同模板版本表（content 即版本内容，可追溯可回滚）
CREATE TABLE IF NOT EXISTS `ct_template_version` (
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
CREATE TABLE IF NOT EXISTS `ct_contract` (
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
-- 菜单与权限（id 285-299，超管角色同步授权）
-- ========================================
INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(285, '合同管理', 0, '/contract', 'Layout', '/contract/template', 'Folder', 10, 0, NULL, 1, 1, 0, 0);

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
(1, 292), (1, 293), (1, 294), (1, 295), (1, 296), (1, 297), (1, 298), (1, 299);

-- ========================================
-- 字典：合同分类（前端维护后须调 reloadDictCache('contract_category')）
-- ========================================
INSERT IGNORE INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(225, '合同分类', 'contract_category', 1, 1, '合同模板/合同分类（value 兼作合同编号分类码段）');

INSERT IGNORE INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2250, 'contract_category', '劳动合同', 'LD', 1, 1, 'primary'),
(2251, 'contract_category', '采购合同', 'CG', 2, 1, 'info'),
(2252, 'contract_category', '销售合同', 'XS', 3, 1, 'success'),
(2253, 'contract_category', '租赁合同', 'ZL', 4, 1, 'warning'),
(2254, 'contract_category', '借款合同', 'JK', 5, 1, 'danger'),
(2255, 'contract_category', '保密协议', 'BM', 6, 1, 'info'),
(2256, 'contract_category', '服务合同', 'FW', 7, 1, 'primary');

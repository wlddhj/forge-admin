-- ========================================
-- 打印模板表 + 菜单/权限码种子数据
-- 创建时间：2026-09-08
-- 说明：从 nbmt 项目迁移 hiprint 模板打印功能
-- ID 段分配：2500-2530（避开大屏 2300-2399、租户 2400-2499、工作流 2200-2299、AI 300-323）
-- ========================================

-- 打印模板配置表
DROP TABLE IF EXISTS `sys_print_template`;
CREATE TABLE `sys_print_template` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` VARCHAR(64)  NOT NULL COMMENT '模板编号（同租户内唯一）',
  `template_name` VARCHAR(100) NOT NULL COMMENT '模板名称',
  `contents`      LONGTEXT     DEFAULT NULL COMMENT '模板内容（hiprint JSON）',
  `version`       INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（0:禁用 1:启用）',
  `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `tenant_id`     BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by`     BIGINT       DEFAULT NULL COMMENT '创建人',
  `update_by`     BIGINT       DEFAULT NULL COMMENT '更新人',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `template_code`, `deleted`) COMMENT '同租户编号唯一',
  KEY `idx_tenant_id` (`tenant_id`) COMMENT '租户索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='打印模板配置表';

-- ========================================
-- 菜单与权限码种子
-- ========================================

-- 打印管理目录（顶级，sort_order=11，位于系统管理之后）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2500, '打印管理', 0, '/system/print', 'Layout', '/system/print/index', 'Printer', 11, 0, NULL, 1, 1, 0, 0);

-- 打印模板菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2510, '打印模板', 2500, '/system/print/index', '/views/system/print/index', NULL, 'Document', 1, 1, 'system:print-template:list', 1, 1, 0, 0);

-- 打印模板按钮权限（query/add/edit/remove）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2511, '模板查询', 2510, '', '', NULL, '', 1, 2, 'system:print-template:query', 1, 1, 0, 0),
(2512, '模板新增', 2510, '', '', NULL, '', 2, 2, 'system:print-template:add', 1, 1, 0, 0),
(2513, '模板编辑', 2510, '', '', NULL, '', 3, 2, 'system:print-template:edit', 1, 1, 0, 0),
(2514, '模板删除', 2510, '', '', NULL, '', 4, 2, 'system:print-template:remove', 1, 1, 0, 0);

-- 模板设计器菜单（不在侧边栏显示，但仍可路由访问）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2520, '模板设计器', 2500, '/system/print/design', '/views/system/print/design/index', NULL, 'EditPen', 2, 1, 'system:print-template:edit', 1, 0, 0, 1);

-- 打印 demo 菜单（可选，作为调用示例）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2530, '打印 Demo', 2500, '/demo/hiprint', '/views/demo/hiprint/index', NULL, 'View', 3, 1, 'system:print-template:demo', 1, 1, 0, 0);

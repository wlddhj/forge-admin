-- ========================================
-- 流程表达式管理
-- ========================================
CREATE TABLE IF NOT EXISTS `wf_process_expression` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` VARCHAR(128) NOT NULL COMMENT '表达式名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0:停用 1:启用)',
    `expression` VARCHAR(512) NOT NULL COMMENT '表达式内容',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程表达式';

-- ========================================
-- 流程监听器管理
-- ========================================
CREATE TABLE IF NOT EXISTS `wf_process_listener` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` VARCHAR(128) NOT NULL COMMENT '监听器名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0:停用 1:启用)',
    `type` VARCHAR(32) NOT NULL COMMENT '监听类型(execution/task)',
    `event` VARCHAR(32) NOT NULL COMMENT '监听事件',
    `value_type` VARCHAR(32) NOT NULL COMMENT '值类型(class/delegateExpression/expression)',
    `value` VARCHAR(512) NOT NULL COMMENT '值',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程监听器';

-- ========================================
-- 流程表达式管理菜单（挂在流程管理目录下）
-- ========================================
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(220, '表达式管理', 100, '/workflow/expression', '/views/workflow/expression/index', NULL, 'Edit', 10, 1, 'workflow:expression:list', 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(221, '表达式查询', 220, '', '', NULL, '', 1, 2, 'workflow:expression:query', 1, 1, 0, 0),
(222, '表达式新增', 220, '', '', NULL, '', 2, 2, 'workflow:expression:add', 1, 1, 0, 0),
(223, '表达式编辑', 220, '', '', NULL, '', 3, 2, 'workflow:expression:edit', 1, 1, 0, 0),
(224, '表达式删除', 220, '', '', NULL, '', 4, 2, 'workflow:expression:delete', 1, 1, 0, 0);

-- ========================================
-- 流程监听器管理菜单（挂在流程管理目录下）
-- ========================================
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(230, '监听器管理', 100, '/workflow/listener', '/views/workflow/listener/index', NULL, 'Bell', 11, 1, 'workflow:listener:list', 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(231, '监听器查询', 230, '', '', NULL, '', 1, 2, 'workflow:listener:query', 1, 1, 0, 0),
(232, '监听器新增', 230, '', '', NULL, '', 2, 2, 'workflow:listener:add', 1, 1, 0, 0),
(233, '监听器编辑', 230, '', '', NULL, '', 3, 2, 'workflow:listener:edit', 1, 1, 0, 0),
(234, '监听器删除', 230, '', '', NULL, '', 4, 2, 'workflow:listener:delete', 1, 1, 0, 0);

-- ========================================
-- 角色授权
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 220), (1, 221), (1, 222), (1, 223), (1, 224),
(1, 230), (1, 231), (1, 232), (1, 233), (1, 234);

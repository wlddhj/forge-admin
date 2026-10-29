-- ========================================
-- 业务示例模块 forge-module-demo（项目立项接入工作流示例）
-- 全新安装用；存量库请执行 upgrade-20261029-project-example.sql
-- 对接文档：docs-base/modules/workflow/06.business-integration-guide.md
-- ========================================

-- 项目立项业务表
CREATE TABLE `demo_biz_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_name` VARCHAR(128) NOT NULL COMMENT '项目名称',
    `total_amount` DECIMAL(14,2) DEFAULT NULL COMMENT '项目金额',
    `biz_user` VARCHAR(64) DEFAULT NULL COMMENT '责任人',
    `dept_name` VARCHAR(64) DEFAULT NULL COMMENT '责任部门',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `process_instance_id` BIGINT DEFAULT NULL COMMENT '流程实例ID（businessKey=project:{id} 发起后回写）',
    `process_no` VARCHAR(64) DEFAULT NULL COMMENT '流程编号（发起回写）',
    `approve_status` VARCHAR(20) NOT NULL DEFAULT 'APPROVING' COMMENT '审批状态 APPROVING/APPROVED/REJECTED/CANCELED（仅由工作流事件回写）',
    `approve_time` DATETIME DEFAULT NULL COMMENT '审批完成时间（终态事件回写）',
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（多租户拦截器自动隔离）',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人（发起人）',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`approve_status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目立项表（业务示例模块）';

-- 项目管理菜单（id 280-284 空闲段）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(280, '示例模块', 0, '/demo', 'Layout', '/demo/project', 'Folder', 9, 0, NULL, 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(281, '项目立项', 280, '/demo/project', '/views/demo/project/index', NULL, 'DocumentChecked', 1, 1, 'demo:project:list', 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(282, '立项查询', 281, '', '', NULL, '', 1, 2, 'demo:project:query', 1, 1, 0, 0),
(283, '立项提交', 281, '', '', NULL, '', 2, 2, 'demo:project:add', 1, 1, 0, 0),
(284, '立项撤销', 281, '', '', NULL, '', 3, 2, 'demo:project:edit', 1, 1, 0, 0);

-- 超管角色授权（role_id=1）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 280), (1, 281), (1, 282), (1, 283), (1, 284);

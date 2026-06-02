-- ========================================
-- forge-admin 工作流模块初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建时间: 2026-06-02
-- 说明: 本脚本仅包含工作流模块相关的表结构和数据
-- 前置依赖: sql/init.sql (系统基础表)
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `forge_admin`;

-- ========================================
-- 1. 流程分类表
-- ========================================
DROP TABLE IF EXISTS `wf_category`;
CREATE TABLE `wf_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_name` varchar(100) NOT NULL COMMENT '分类名称',
  `category_code` varchar(100) NOT NULL COMMENT '分类编码',
  `parent_id` bigint DEFAULT 0 COMMENT '父分类ID',
  `sort_order` int DEFAULT 0 COMMENT '排序',
  `status` tinyint DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程分类表';

-- ========================================
-- 2. 流程部署扩展表
-- ========================================
DROP TABLE IF EXISTS `wf_process_deploy_ext`;
CREATE TABLE `wf_process_deploy_ext` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deployment_id` varchar(64) NOT NULL COMMENT 'Flowable部署ID',
  `process_definition_id` varchar(64) NOT NULL COMMENT 'Flowable流程定义ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程标识',
  `process_name` varchar(200) NOT NULL COMMENT '流程名称',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `description` text DEFAULT NULL COMMENT '流程描述',
  `form_type` tinyint DEFAULT NULL COMMENT '表单类型(10流程表单 20业务表单)',
  `form_id` bigint DEFAULT NULL COMMENT '关联表单ID',
  `auto_copy_strategy` int DEFAULT NULL COMMENT '自动抄送策略',
  `auto_copy_param` varchar(512) DEFAULT NULL COMMENT '自动抄送参数',
  `form_key` varchar(200) DEFAULT NULL COMMENT '表单标识',
  `bpmn_xml` longtext DEFAULT NULL COMMENT 'BPMN XML内容',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_by_name` varchar(100) DEFAULT NULL COMMENT '创建人名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_deployment_id` (`deployment_id`),
  KEY `idx_process_key` (`process_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程部署扩展表';

-- ========================================
-- 3. 审批意见表
-- ========================================
DROP TABLE IF EXISTS `wf_approval_comment`;
CREATE TABLE `wf_approval_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_instance_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) NOT NULL COMMENT '任务ID',
  `task_def_key` varchar(200) DEFAULT NULL COMMENT '任务定义Key',
  `task_name` varchar(200) DEFAULT NULL COMMENT '任务名称',
  `user_id` bigint NOT NULL COMMENT '审批人ID',
  `user_name` varchar(100) DEFAULT NULL COMMENT '审批人名称',
  `action_type` varchar(30) NOT NULL COMMENT '操作类型(approve/reject/delegate/transfer/return/claim)',
  `comment_text` text DEFAULT NULL COMMENT '审批意见',
  `attachment_ids` varchar(500) DEFAULT NULL COMMENT '附件ID列表(逗号分隔)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批意见表';

-- ========================================
-- 4. 工作流表单管理表
-- ========================================
DROP TABLE IF EXISTS `wf_form`;
CREATE TABLE `wf_form` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` varchar(64) NOT NULL COMMENT '表单名称',
    `status` tinyint DEFAULT 0 COMMENT '状态(0正常 1停用)',
    `conf` text COMMENT '表单配置(JSON)',
    `fields` text COMMENT '表单字段列表(JSON数组)',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_by` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表单';

-- ========================================
-- 5. 流程表达式管理
-- ========================================
DROP TABLE IF EXISTS `wf_process_expression`;
CREATE TABLE `wf_process_expression` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` varchar(128) NOT NULL COMMENT '表达式名称',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:停用 1:启用)',
    `expression` varchar(512) NOT NULL COMMENT '表达式内容',
    `remark` varchar(512) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` bigint DEFAULT NULL COMMENT '创建人',
    `update_by` bigint DEFAULT NULL COMMENT '更新人',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程表达式';

-- ========================================
-- 6. 流程监听器管理
-- ========================================
DROP TABLE IF EXISTS `wf_process_listener`;
CREATE TABLE `wf_process_listener` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` varchar(128) NOT NULL COMMENT '监听器名称',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:停用 1:启用)',
    `type` varchar(32) NOT NULL COMMENT '监听类型(execution/task)',
    `event` varchar(32) NOT NULL COMMENT '监听事件',
    `value_type` varchar(32) NOT NULL COMMENT '值类型(class/delegateExpression/expression)',
    `value` varchar(512) NOT NULL COMMENT '值',
    `remark` varchar(512) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` bigint DEFAULT NULL COMMENT '创建人',
    `update_by` bigint DEFAULT NULL COMMENT '更新人',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程监听器';

-- ========================================
-- 7. 流程抄送记录表
-- ========================================
DROP TABLE IF EXISTS `wf_process_instance_copy`;
CREATE TABLE `wf_process_instance_copy` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `start_user_id` bigint DEFAULT NULL COMMENT '发起人ID',
    `process_instance_name` varchar(255) DEFAULT NULL COMMENT '流程实例名称',
    `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
    `process_no` varchar(32) DEFAULT NULL COMMENT '流程编号',
    `process_definition_id` varchar(64) DEFAULT NULL COMMENT '流程定义ID',
    `category` varchar(64) DEFAULT NULL COMMENT '流程分类',
    `activity_id` varchar(64) DEFAULT NULL COMMENT '活动节点ID',
    `activity_name` varchar(255) DEFAULT NULL COMMENT '活动节点名称',
    `task_id` varchar(64) DEFAULT NULL COMMENT '任务ID',
    `user_id` bigint NOT NULL COMMENT '被抄送用户ID',
    `reason` varchar(512) DEFAULT NULL COMMENT '抄送原因',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by` bigint DEFAULT NULL COMMENT '创建人',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程抄送记录';

-- ========================================
-- 工作流模块菜单
-- ========================================

-- 流程管理目录（顶级目录）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(100, '流程管理', 0, '/workflow', 'Layout', '/workflow/process', 'SetUp', 2, 0, NULL, 1, 1, 0, 0);

-- 流程设计菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(101, '流程设计', 100, '/workflow/process', '/views/workflow/process/index', NULL, 'EditPen', 1, 1, 'workflow:process:list', 1, 1, 0, 0);

-- 流程设计按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(102, '流程查询', 101, '', '', NULL, '', 1, 2, 'workflow:process:query', 1, 1, 0, 0),
(103, '流程新增', 101, '', '', NULL, '', 2, 2, 'workflow:process:add', 1, 1, 0, 0),
(104, '流程编辑', 101, '', '', NULL, '', 3, 2, 'workflow:process:edit', 1, 1, 0, 0),
(105, '流程部署', 101, '', '', NULL, '', 4, 2, 'workflow:process:deploy', 1, 1, 0, 0),
(106, '流程删除', 101, '', '', NULL, '', 5, 2, 'workflow:process:delete', 1, 1, 0, 0);

-- 流程分类菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(107, '流程分类', 100, '/workflow/category', '/views/workflow/category/index', NULL, 'FolderOpened', 2, 1, 'workflow:category:list', 1, 1, 0, 0);

-- 流程分类按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(108, '分类查询', 107, '', '', NULL, '', 1, 2, 'workflow:category:query', 1, 1, 0, 0),
(109, '分类新增', 107, '', '', NULL, '', 2, 2, 'workflow:category:add', 1, 1, 0, 0),
(110, '分类编辑', 107, '', '', NULL, '', 3, 2, 'workflow:category:edit', 1, 1, 0, 0),
(111, '分类删除', 107, '', '', NULL, '', 4, 2, 'workflow:category:delete', 1, 1, 0, 0);

-- 流程实例菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(112, '流程实例', 100, '/workflow/instance', '/views/workflow/instance/index', NULL, 'Tickets', 3, 1, 'workflow:instance:list', 1, 1, 0, 0);

-- 流程实例按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(113, '实例查询', 112, '', '', NULL, '', 1, 2, 'workflow:instance:query', 1, 1, 0, 0),
(114, '发起流程', 112, '', '', NULL, '', 2, 2, 'workflow:instance:start', 1, 1, 0, 0),
(115, '取消流程', 112, '', '', NULL, '', 3, 2, 'workflow:instance:cancel', 1, 1, 0, 0);

-- 待办任务菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(116, '待办任务', 100, '/workflow/task/todo', '/views/workflow/task/TodoTask', NULL, 'Bell', 4, 1, 'workflow:task:list', 1, 1, 0, 0);

-- 待办任务按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(117, '任务查询', 116, '', '', NULL, '', 1, 2, 'workflow:task:query', 1, 1, 0, 0),
(118, '任务认领', 116, '', '', NULL, '', 2, 2, 'workflow:task:claim', 1, 1, 0, 0),
(119, '任务完成', 116, '', '', NULL, '', 3, 2, 'workflow:task:complete', 1, 1, 0, 0),
(120, '任务委派', 116, '', '', NULL, '', 4, 2, 'workflow:task:delegate', 1, 1, 0, 0),
(121, '任务转办', 116, '', '', NULL, '', 5, 2, 'workflow:task:transfer', 1, 1, 0, 0),
(122, '任务退回', 116, '', '', NULL, '', 6, 2, 'workflow:task:return', 1, 1, 0, 0);

-- 已办任务菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(123, '已办任务', 100, '/workflow/task/done', '/views/workflow/task/DoneTask', NULL, 'Finished', 5, 1, 'workflow:task:list', 1, 1, 0, 0);

-- 隐藏菜单（设计器、详情页面，visible=0 不在侧边栏显示）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(124, '流程设计器', 101, '/workflow/process/designer', '/views/workflow/process/ProcessDesigner', NULL, '', 10, 1, NULL, 1, 0, 0, 0),
(125, '实例详情', 112, '/workflow/instance/detail', '/views/workflow/instance/InstanceDetail', NULL, '', 10, 1, NULL, 1, 0, 0, 0),
(126, '任务详情', 116, '/workflow/task/detail', '/views/workflow/task/TaskDetail', NULL, '', 10, 1, NULL, 1, 0, 0, 0);

-- 模型管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(210, '模型管理', 100, '/workflow/model', '/views/workflow/model/index', NULL, 'Files', 2, 1, 'workflow:model:list', 1, 1, 0, 0);

-- 模型管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(211, '模型查询', 210, '', '', NULL, '', 1, 2, 'workflow:model:query', 1, 1, 0, 0),
(212, '模型新增', 210, '', '', NULL, '', 2, 2, 'workflow:model:add', 1, 1, 0, 0),
(213, '模型编辑', 210, '', '', NULL, '', 3, 2, 'workflow:model:edit', 1, 1, 0, 0),
(214, '模型部署', 210, '', '', NULL, '', 4, 2, 'workflow:model:deploy', 1, 1, 0, 0),
(215, '模型删除', 210, '', '', NULL, '', 5, 2, 'workflow:model:delete', 1, 1, 0, 0),
(216, '模型设计', 210, '/workflow/model/designer', '/views/workflow/model/ModelDesigner', NULL, '', 10, 1, NULL, 1, 0, 0, 0);

-- 表单管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(200, '表单管理', 100, '/workflow/form', '/views/workflow/form/index', NULL, 'Document', 1, 1, 'workflow:form:list', 1, 1, 0, 0);

-- 表单设计器路由（hidden）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(201, '表单设计器', 200, '/workflow/form/editor', '/views/workflow/form/FormEditor', NULL, '', 1, 1, 'workflow:form:edit', 1, 0, 0, 0);

-- 表单管理按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(202, '表单查询', 200, '', '', NULL, '', 1, 2, 'workflow:form:query', 1, 1, 0, 0),
(203, '表单新增', 200, '', '', NULL, '', 2, 2, 'workflow:form:add', 1, 1, 0, 0),
(204, '表单编辑', 200, '', '', NULL, '', 3, 2, 'workflow:form:edit', 1, 1, 0, 0),
(205, '表单删除', 200, '', '', NULL, '', 4, 2, 'workflow:form:delete', 1, 1, 0, 0);

-- 表达式管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(220, '表达式管理', 100, '/workflow/expression', '/views/workflow/expression/index', NULL, 'Edit', 10, 1, 'workflow:expression:list', 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(221, '表达式查询', 220, '', '', NULL, '', 1, 2, 'workflow:expression:query', 1, 1, 0, 0),
(222, '表达式新增', 220, '', '', NULL, '', 2, 2, 'workflow:expression:add', 1, 1, 0, 0),
(223, '表达式编辑', 220, '', '', NULL, '', 3, 2, 'workflow:expression:edit', 1, 1, 0, 0),
(224, '表达式删除', 220, '', '', NULL, '', 4, 2, 'workflow:expression:delete', 1, 1, 0, 0);

-- 监听器管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(230, '监听器管理', 100, '/workflow/listener', '/views/workflow/listener/index', NULL, 'Bell', 11, 1, 'workflow:listener:list', 1, 1, 0, 0);

INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(231, '监听器查询', 230, '', '', NULL, '', 1, 2, 'workflow:listener:query', 1, 1, 0, 0),
(232, '监听器新增', 230, '', '', NULL, '', 2, 2, 'workflow:listener:add', 1, 1, 0, 0),
(233, '监听器编辑', 230, '', '', NULL, '', 3, 2, 'workflow:listener:edit', 1, 1, 0, 0),
(234, '监听器删除', 230, '', '', NULL, '', 4, 2, 'workflow:listener:delete', 1, 1, 0, 0);

-- 抄送列表菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(240, '抄送列表', 100, '/workflow/copy', '/views/workflow/task/CopyTask', NULL, 'Message', 12, 1, 'workflow:task:list', 1, 1, 0, 0);

-- ========================================
-- 角色授权（超级管理员拥有所有工作流菜单）
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 100), (1, 101), (1, 102), (1, 103), (1, 104), (1, 105), (1, 106),
(1, 107), (1, 108), (1, 109), (1, 110), (1, 111),
(1, 112), (1, 113), (1, 114), (1, 115),
(1, 116), (1, 117), (1, 118), (1, 119), (1, 120), (1, 121), (1, 122),
(1, 123), (1, 124), (1, 125), (1, 126),
(1, 200), (1, 201), (1, 202), (1, 203), (1, 204), (1, 205),
(1, 210), (1, 211), (1, 212), (1, 213), (1, 214), (1, 215), (1, 216),
(1, 220), (1, 221), (1, 222), (1, 223), (1, 224),
(1, 230), (1, 231), (1, 232), (1, 233), (1, 234),
(1, 240);

-- ========================================
-- 工作流字典数据
-- ========================================

-- 审批操作类型
INSERT INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(220, '审批操作类型', 'wf_action_type', 1, 1, '流程审批操作类型');

INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2201, 'wf_action_type', '提交',   'submit',       1,  1, 'primary'),
(2202, 'wf_action_type', '通过',   'approve',      2,  1, 'success'),
(2203, 'wf_action_type', '驳回',   'reject',       3,  1, 'danger'),
(2204, 'wf_action_type', '委派',   'delegate',     4,  1, 'warning'),
(2205, 'wf_action_type', '转办',   'transfer',     5,  1, 'warning'),
(2206, 'wf_action_type', '退回',   'return',       6,  1, 'info'),
(2207, 'wf_action_type', '撤回',   'withdraw',     7,  1, 'warning'),
(2208, 'wf_action_type', '抄送',   'copy',         8,  1, 'info'),
(2209, 'wf_action_type', '认领',   'claim',        9,  1, 'primary'),
(2210, 'wf_action_type', '取消',   'cancel',       10, 1, 'danger'),
(2211, 'wf_action_type', '加签',   'sign_create',  11, 1, 'primary'),
(2212, 'wf_action_type', '减签',   'sign_delete',  12, 1, 'primary');

-- 流程编号序列种子数据
INSERT INTO `sys_key_sequence` (`key_category`, `key_prefix`, `date_rule`, `seq_length`, `remark`) VALUES
('process_no', 'WF', 'yyyyMMdd', 4, '流程编号');

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT '工作流模块初始化完成!' AS message;

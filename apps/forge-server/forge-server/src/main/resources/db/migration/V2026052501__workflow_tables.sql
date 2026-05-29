-- ========================================
-- BPMN 2.0 工作流模块 - 表结构和菜单
-- ========================================

-- 流程分类表
CREATE TABLE IF NOT EXISTS `wf_category` (
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

-- 流程部署扩展表
CREATE TABLE IF NOT EXISTS `wf_process_deploy_ext` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deployment_id` varchar(64) NOT NULL COMMENT 'Flowable部署ID',
  `process_definition_id` varchar(64) NOT NULL COMMENT 'Flowable流程定义ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程标识',
  `process_name` varchar(200) NOT NULL COMMENT '流程名称',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `description` text DEFAULT NULL COMMENT '流程描述',
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

-- 审批意见表
CREATE TABLE IF NOT EXISTS `wf_approval_comment` (
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

-- 超级管理员角色授予所有新菜单权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 100), (1, 101), (1, 102), (1, 103), (1, 104), (1, 105), (1, 106),
(1, 107), (1, 108), (1, 109), (1, 110), (1, 111),
(1, 112), (1, 113), (1, 114), (1, 115),
(1, 116), (1, 117), (1, 118), (1, 119), (1, 120), (1, 121), (1, 122),
(1, 123), (1, 124), (1, 125), (1, 126);

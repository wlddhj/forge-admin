-- ========================================
-- 工作流迭代二：暴露引擎能力·设计器补节点
-- 1) wf_process_ext 发起范围人员名单（flw_process.use_scope 为 0/1/2 开关，名单存扩展表）
-- 2) 拿回按钮权限 workflow:task:return（挂待办任务 116）
-- 3) 实例唤醒按钮权限 workflow:instance:resume（挂流程实例 112）
-- 创建时间：2026-10-25
-- ========================================

ALTER TABLE `wf_process_ext` ADD COLUMN `use_scope_users` VARCHAR(2000) NULL COMMENT '发起范围人员名单JSON(用户ID数组),use_scope=1时生效' AFTER `auto_copy_param`;

ALTER TABLE `wf_process_ext` ADD COLUMN `use_scope` INT NULL DEFAULT 0 COMMENT '发起范围 0全员 1指定人员 2均不可发起' AFTER `auto_copy_param`;

DELETE FROM `sys_role_menu` WHERE `menu_id` = 242;
DELETE FROM `sys_menu` WHERE `id` = 242;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(242, '任务拿回', 116, '', '', NULL, '', 7, 2, 'workflow:task:reclaim', 1, 1, 0, 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 242
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 116;

DELETE FROM `sys_role_menu` WHERE `menu_id` = 243;
DELETE FROM `sys_menu` WHERE `id` = 243;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(243, '实例唤醒', 112, '', '', NULL, '', 5, 2, 'workflow:instance:resume', 1, 1, 0, 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 243
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 112;

DELETE FROM `sys_dict_data` WHERE `dict_type` = 'wf_action_type' AND `dict_value` IN ('reclaim', 'resume');
INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`, `remark`, `create_time`, `update_time`) VALUES
(2214, 'wf_action_type', '拿回', 'reclaim', 14, 1, 'warning', NULL, NOW(), NOW()),
(2215, 'wf_action_type', '唤醒', 'resume', 15, 1, 'primary', NULL, NOW(), NOW());

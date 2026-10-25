-- ========================================
-- 工作流迭代一：补断点与正确性风险
-- 1) 删除死表 wf_process_instance_copy（自创建起无 entity/mapper/写入，
--    抄送记录实际存于 flw_his_task(taskType=cc) + flw_his_task_actor(weight=6)）
-- 2) 流程实例催办按钮权限 workflow:instance:urge（挂流程实例菜单 112 下）
-- 3) 审批操作类型字典补充「催办」
-- 创建时间：2026-10-25
-- ========================================

DROP TABLE IF EXISTS `wf_process_instance_copy`;

DELETE FROM `sys_role_menu` WHERE `menu_id` = 241;
DELETE FROM `sys_menu` WHERE `id` = 241;
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(241, '流程催办', 112, '', '', NULL, '', 4, 2, 'workflow:instance:urge', 1, 1, 0, 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 241
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 112;

DELETE FROM `sys_dict_data` WHERE `dict_type` = 'wf_action_type' AND `dict_value` = 'urge';
INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`, `remark`, `create_time`, `update_time`) VALUES
(2213, 'wf_action_type', '催办', 'urge', 13, 1, 'danger', NULL, NOW(), NOW());

-- 紧急程度字典化（urgent 码值渲染统一走 wf_urgent 字典，前后端去硬编码）
-- 幂等可重跑；dict id 段 224/2241-2244 于 2026-10-29 核对空闲
INSERT IGNORE INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(224, '流程紧急程度', 'wf_urgent', 1, 1, '流程实例/任务紧急程度（flowlong urgent）');

INSERT IGNORE INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2245, 'wf_urgent', '常规',       '0', 1, 1, 'info'),
(2242, 'wf_urgent', '重要不紧急', '1', 2, 1, 'primary'),
(2243, 'wf_urgent', '紧急不重要', '2', 3, 1, 'warning'),
(2244, 'wf_urgent', '紧急且重要', '3', 4, 1, 'danger');

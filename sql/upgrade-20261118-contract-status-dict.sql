-- ============================================================
-- 升级SQL：合同状态字典化——新增 contract_status 字典
-- 日期：2026-11-18
-- 说明：审批侧业务单据视图（ContractBizViewProvider）的「合同状态」label
--       渲染改走数据字典（此前为后端硬编码 switch）；字典缺失时后端回退
--       内置映射，不阻断审批界面。码值对齐 ct_contract.status：
--         0 草稿 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已撤销
-- 注意：dev 库未启用 Flyway，请在 forge_admin 库手工执行本脚本（不写 USE，选库由执行方决定）
-- 幂等性：INSERT IGNORE，已存在则跳过；dict id 段 225/2251-2255 于 2026-11-18 核对空闲
-- ============================================================

INSERT IGNORE INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(225, '合同状态', 'contract_status', 1, 1, '合同审批状态（ct_contract.status）');

INSERT IGNORE INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2251, 'contract_status', '草稿',   '0', 1, 1, 'info'),
(2252, 'contract_status', '审批中', '1', 2, 1, 'primary'),
(2253, 'contract_status', '已通过', '2', 3, 1, 'success'),
(2254, 'contract_status', '已驳回', '3', 4, 1, 'danger'),
(2255, 'contract_status', '已撤销', '4', 5, 1, 'warning');

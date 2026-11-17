-- ============================================================
-- 升级SQL：合同状态字典化——新增 contract_status 字典
-- 日期：2026-11-18
-- 说明：审批侧业务单据视图（ContractBizViewProvider）与合同前端（列表/详情/筛选
--       dict-value）的「合同状态」渲染统一走本字典。码值对齐 ct_contract.status：
--         0 草稿 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已撤销
-- 注意：dev 库未启用 Flyway，请在 forge_admin 库手工执行本脚本（不写 USE，选库由执行方决定）
-- 幂等性：INSERT IGNORE + UPDATE，可重复跑。
--         id 修正记录：首版误用 225/2251-2255——225 已被 contract_category（合同分类）
--         占用，dev 已按 226/2257-2261 手工插入，脚本随库对齐（2026-11-18）。
--         新库部署按本 id 段执行即可；dict id 核对须连库查，勿只 grep 脚本。
-- ============================================================

INSERT IGNORE INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(226, '合同状态', 'contract_status', 1, 1, '合同审批状态（ct_contract.status）');

INSERT IGNORE INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2257, 'contract_status', '草稿',   '0', 1, 1, 'info'),
(2258, 'contract_status', '审批中', '1', 2, 1, 'warning'),
(2259, 'contract_status', '已通过', '2', 3, 1, 'success'),
(2260, 'contract_status', '已驳回', '3', 4, 1, 'danger'),
(2261, 'contract_status', '已撤销', '4', 5, 1, 'warning');

-- 已插入旧配色（审批中=primary）的库对齐前端原视觉（warning），UPDATE 天然幂等
UPDATE `sys_dict_data` SET `list_class` = 'warning'
WHERE `id` = 2258 AND `dict_type` = 'contract_status' AND `list_class` = 'primary';

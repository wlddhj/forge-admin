-- ============================================================
-- 升级SQL：demo 项目立项状态字典化——新增 demo_project_status 字典
-- 日期：2026-11-18
-- 说明：项目立项 approveStatusName 渲染改走数据字典（原为 DTO 内硬编码
--       switch），后端统一经 DictLabelResolver 解析。码值为字符串枚举，
--       对齐 demo_biz_project.approve_status：
--         APPROVING 审批中 / APPROVED 已通过 / REJECTED 已驳回 / CANCELED 已撤销
-- 注意：dev 库未启用 Flyway，请在 forge_admin 库手工执行本脚本（不写 USE，选库由执行方决定）
-- 幂等性：INSERT IGNORE，可重复跑。
--         id 修正记录：首版误用 226/2261-2264——226 已被 contract_status 占用，
--         dev 已按 227/2262-2265 手工插入，脚本随库对齐（2026-11-18）。
--         新库部署按本 id 段执行即可；dict id 核对须连库查，勿只 grep 脚本。
-- ============================================================

INSERT IGNORE INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(227, '项目立项状态', 'demo_project_status', 1, 1, 'demo 项目立项审批状态（demo_biz_project.approve_status）');

INSERT IGNORE INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) VALUES
(2262, 'demo_project_status', '审批中', 'APPROVING', 1, 1, 'primary'),
(2263, 'demo_project_status', '已通过', 'APPROVED',  2, 1, 'success'),
(2264, 'demo_project_status', '已驳回', 'REJECTED',  3, 1, 'danger'),
(2265, 'demo_project_status', '已撤销', 'CANCELED',  4, 1, 'warning');

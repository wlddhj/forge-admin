-- ============================================================
-- 升级SQL：业务表单视图——存量流程模型 form_type 置 20（业务表单）
-- 日期：2026-11-17
-- 说明：审批抽屉/流程详情内嵌业务单据视图（WfBizViewProvider SPI）。
--       wf_process_ext 无结构变更（form_type=20 语义做实），本脚本仅把
--       已由业务模块承载单据的两类存量模型的 form_type 从 NULL 置 20，
--       使审批界面走「业务单据」渲染通道：
--         contract_approval —— 合同模块（ContractBizViewProvider）
--         project_approval —— demo 项目立项（ProjectBizViewProvider）
-- 注意：dev 库未启用 Flyway，请在 forge_admin 库手工执行本脚本（不写 USE，选库由执行方决定）
-- 幂等性：UPDATE 天然幂等；仅覆盖 form_type IS NULL 的行，管理员显式配置过的
--         模型（10 流程表单）不动
-- ============================================================

UPDATE `wf_process_ext`
SET `form_type` = 20,
    `update_time` = NOW()
WHERE `process_key` IN ('contract_approval', 'project_approval')
  AND `form_type` IS NULL
  AND `deleted` = 0;

-- ========================================
-- wf_process_ext 表结构修复 - 支持模型草稿状态
-- V2026062401__wf_process_ext_allow_null.sql
-- ========================================

-- process_id 允许 NULL，支持未部署的模型草稿
-- FlowLong 没有独立的模型表，使用 wf_process_ext 存储模型草稿：
-- - 未部署的模型：process_id = null，表示草稿状态
-- - 已部署的模型：process_id 有值，关联 flw_process 表
ALTER TABLE wf_process_ext MODIFY COLUMN process_id BIGINT NULL COMMENT 'FlowLong流程定义ID（关联flw_process.id，NULL表示草稿状态）';
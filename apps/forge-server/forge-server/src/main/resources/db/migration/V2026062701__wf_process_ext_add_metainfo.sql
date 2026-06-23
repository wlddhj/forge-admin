-- ========================================
-- 添加 wf_process_ext 表 metaInfo 和 modelJson 字段
-- V2026062701__wf_process_ext_add_metainfo.sql
-- ========================================

-- 添加 metaInfo 字段用于存储表单配置等扩展信息
ALTER TABLE wf_process_ext ADD COLUMN meta_info TEXT COMMENT '元信息JSON（存储表单配置等扩展信息）' AFTER bpmn_xml;

-- 添加 modelJson 字段用于存储 FlowLong 流程模型 JSON
ALTER TABLE wf_process_ext ADD COLUMN model_json LONGTEXT COMMENT 'FlowLong流程模型JSON内容' AFTER meta_info;
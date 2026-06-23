-- ========================================
-- 改造自定义表
-- V2026062601__modify_workflow_custom_tables.sql
-- ========================================

-- wf_process_deploy_ext 改造为 wf_process_ext
-- 删除 Flowable 相关字段，保留业务扩展字段，关联 FlowLong 流程定义

-- 1. 删除原表（包含 Flowable 相关字段）
DROP TABLE IF EXISTS wf_process_deploy_ext;

-- 2. 创建新的扩展表（关联 FlowLong 流程定义，保留分类关联）
CREATE TABLE wf_process_ext (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    process_id BIGINT NOT NULL COMMENT 'FlowLong流程定义ID（关联flw_process.id）',
    process_key VARCHAR(64) NOT NULL COMMENT '流程标识',
    process_name VARCHAR(128) NOT NULL COMMENT '流程名称',
    category_id BIGINT COMMENT '分类ID（关联wf_category.id）',
    description TEXT COMMENT '流程描述',
    form_type INT COMMENT '表单类型(10流程表单 20业务表单)',
    form_id BIGINT COMMENT '关联表单ID',
    auto_copy_strategy INT COMMENT '自动抄送策略',
    auto_copy_param VARCHAR(512) COMMENT '自动抄送参数',
    create_by BIGINT COMMENT '创建人ID',
    create_by_name VARCHAR(100) COMMENT '创建人名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    KEY idx_process_id (process_id),
    KEY idx_process_key (process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义扩展表';

-- wf_approval_comment 字段类型调整（String -> Long）
-- 保留此表用于存储审批意见文本和附件，补充 FlowLong 缺失的信息
-- 由于旧数据为 UUID 格式字符串，需要先清空数据再修改字段类型
DELETE FROM wf_approval_comment;
ALTER TABLE wf_approval_comment MODIFY COLUMN process_instance_id BIGINT COMMENT '流程实例ID';
ALTER TABLE wf_approval_comment MODIFY COLUMN task_id BIGINT COMMENT '任务ID';
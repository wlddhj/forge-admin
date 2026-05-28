-- 流程部署扩展表添加自动抄送配置字段
ALTER TABLE wf_process_deploy_ext
    ADD COLUMN auto_copy_strategy INT DEFAULT NULL COMMENT '自动抄送策略' AFTER form_id,
    ADD COLUMN auto_copy_param VARCHAR(512) DEFAULT NULL COMMENT '自动抄送参数' AFTER auto_copy_strategy;

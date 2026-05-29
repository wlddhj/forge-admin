-- 流程编号：抄送表增加 process_no 字段
ALTER TABLE wf_process_instance_copy ADD COLUMN process_no VARCHAR(32) DEFAULT NULL COMMENT '流程编号' AFTER process_instance_id;

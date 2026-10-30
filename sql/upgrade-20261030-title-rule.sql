-- 流程标题规则：模型配置 ${} 模板，发起时渲染存实例变量 __title（零引擎 DDL）
ALTER TABLE `wf_process_ext` ADD COLUMN `title_rule` VARCHAR(500) DEFAULT NULL COMMENT '标题规则（${}模板：内置 processName/initiatorName/businessKey/date + 表单变量）' AFTER `use_scope_users`;

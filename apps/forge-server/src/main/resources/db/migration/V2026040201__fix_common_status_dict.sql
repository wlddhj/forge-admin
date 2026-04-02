-- 修复 sys_common_status 字典值：与数据库 status 字段定义一致（0=失败 1=成功）
-- 原字典值：成功=0 失败=1（反了）
-- 修正后：成功=1 失败=0
UPDATE sys_dict_data SET dict_value = '1' WHERE dict_type = 'sys_common_status' AND dict_label = '成功';
UPDATE sys_dict_data SET dict_value = '0' WHERE dict_type = 'sys_common_status' AND dict_label = '失败';

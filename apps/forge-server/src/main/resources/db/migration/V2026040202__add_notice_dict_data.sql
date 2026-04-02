-- 添加通知公告类型和状态的字典数据
-- init.sql 中已存在 dict_type 定义（id=6,7），但缺少 dict_data

-- 通知类型：通知=1, 公告=2
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status, list_class, remark, create_time, update_time) VALUES
(1040, 'sys_notice_type', '通知', '1', 1, 1, 'primary', '通知类型', NOW(), NOW()),
(1041, 'sys_notice_type', '公告', '2', 2, 1, 'success', '公告类型', NOW(), NOW());

-- 通知状态：关闭=0, 正常=1
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status, list_class, remark, create_time, update_time) VALUES
(1050, 'sys_notice_status', '关闭', '0', 1, 1, 'danger', '关闭状态', NOW(), NOW()),
(1051, 'sys_notice_status', '正常', '1', 2, 1, 'success', '正常状态', NOW(), NOW());

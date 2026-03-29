-- 添加缺失的数据字典类型和数据
-- 用于前端下拉框选项

-- ==================== 字典类型 ====================

-- 数据权限范围
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark, create_time, update_time)
VALUES (100, '数据权限', 'sys_data_scope', 1, '数据权限范围', NOW(), NOW());

-- 配置参数类型
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark, create_time, update_time)
VALUES (101, '参数类型', 'sys_config_type', 1, '配置参数类型', NOW(), NOW());

-- 标签样式
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark, create_time, update_time)
VALUES (102, '标签样式', 'sys_tag_type', 1, '表格标签样式', NOW(), NOW());

-- 文件存储类型
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark, create_time, update_time)
VALUES (103, '存储类型', 'sys_storage_type', 1, '文件存储类型', NOW(), NOW());

-- ==================== 字典数据 ====================

-- 数据权限范围
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status, list_class, remark, create_time, update_time) VALUES
(1001, 'sys_data_scope', '全部数据权限', '1', 1, 1, 'primary', '可查看所有数据', NOW(), NOW()),
(1002, 'sys_data_scope', '自定义数据权限', '2', 2, 1, 'success', '可查看自定义部门数据', NOW(), NOW()),
(1003, 'sys_data_scope', '本部门数据权限', '3', 3, 1, 'info', '可查看本部门数据', NOW(), NOW()),
(1004, 'sys_data_scope', '本部门及以下数据权限', '4', 4, 1, 'warning', '可查看本部门及下级部门数据', NOW(), NOW()),
(1005, 'sys_data_scope', '仅本人数据权限', '5', 5, 1, 'danger', '只能查看本人数据', NOW(), NOW());

-- 配置参数类型
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status, list_class, remark, create_time, update_time) VALUES
(1010, 'sys_config_type', '文本', 'text', 1, 1, 'primary', '文本类型', NOW(), NOW()),
(1011, 'sys_config_type', '数字', 'number', 2, 1, 'success', '数字类型', NOW(), NOW()),
(1012, 'sys_config_type', '布尔', 'boolean', 3, 1, 'info', '布尔类型', NOW(), NOW()),
(1013, 'sys_config_type', 'JSON', 'json', 4, 1, 'warning', 'JSON类型', NOW(), NOW());

-- 标签样式
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status, list_class, remark, create_time, update_time) VALUES
(1020, 'sys_tag_type', '默认', 'default', 1, 1, 'info', '默认样式', NOW(), NOW()),
(1021, 'sys_tag_type', '主要', 'primary', 2, 1, 'primary', '主要样式', NOW(), NOW()),
(1022, 'sys_tag_type', '成功', 'success', 3, 1, 'success', '成功样式', NOW(), NOW()),
(1023, 'sys_tag_type', '警告', 'warning', 4, 1, 'warning', '警告样式', NOW(), NOW()),
(1024, 'sys_tag_type', '危险', 'danger', 5, 1, 'danger', '危险样式', NOW(), NOW()),
(1025, 'sys_tag_type', '信息', 'info', 6, 1, 'info', '信息样式', NOW(), NOW());

-- 文件存储类型
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status, list_class, remark, create_time, update_time) VALUES
(1030, 'sys_storage_type', '本地存储', 'local', 1, 1, 'primary', '本地文件存储', NOW(), NOW()),
(1031, 'sys_storage_type', '阿里云OSS', 'aliyun_oss', 2, 1, 'success', '阿里云对象存储', NOW(), NOW()),
(1032, 'sys_storage_type', '腾讯云COS', 'tencent_cos', 3, 1, 'warning', '腾讯云对象存储', NOW(), NOW()),
(1033, 'sys_storage_type', 'MinIO', 'minio', 4, 1, 'info', 'MinIO对象存储', NOW(), NOW());

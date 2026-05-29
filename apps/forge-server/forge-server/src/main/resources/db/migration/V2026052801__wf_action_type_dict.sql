-- 审批操作类型字典
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, is_system, remark, create_time, update_time)
VALUES (220, '审批操作类型', 'wf_action_type', 1, 1, '流程审批操作类型', NOW(), NOW());

INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status, list_class, remark, create_time, update_time) VALUES
(2201, 'wf_action_type', '提交',   'submit',       1,  1, 'primary', NULL, NOW(), NOW()),
(2202, 'wf_action_type', '通过',   'approve',      2,  1, 'success', NULL, NOW(), NOW()),
(2203, 'wf_action_type', '驳回',   'reject',       3,  1, 'danger',  NULL, NOW(), NOW()),
(2204, 'wf_action_type', '委派',   'delegate',     4,  1, 'warning', NULL, NOW(), NOW()),
(2205, 'wf_action_type', '转办',   'transfer',     5,  1, 'warning', NULL, NOW(), NOW()),
(2206, 'wf_action_type', '退回',   'return',       6,  1, 'info',    NULL, NOW(), NOW()),
(2207, 'wf_action_type', '撤回',   'withdraw',     7,  1, 'warning', NULL, NOW(), NOW()),
(2208, 'wf_action_type', '抄送',   'copy',         8,  1, 'info',    NULL, NOW(), NOW()),
(2209, 'wf_action_type', '认领',   'claim',        9,  1, 'primary', NULL, NOW(), NOW()),
(2210, 'wf_action_type', '取消',   'cancel',       10, 1, 'danger',  NULL, NOW(), NOW()),
(2211, 'wf_action_type', '加签',   'sign_create',  11, 1, 'primary', NULL, NOW(), NOW()),
(2212, 'wf_action_type', '减签',   'sign_delete',  12, 1, 'primary', NULL, NOW(), NOW());

-- 规范化历史数据：将大写的 action_type 统一为小写
UPDATE wf_approval_comment SET action_type = 'copy'        WHERE action_type = 'COPY';
UPDATE wf_approval_comment SET action_type = 'sign_create' WHERE action_type = 'SIGN_CREATE';
UPDATE wf_approval_comment SET action_type = 'sign_delete' WHERE action_type = 'SIGN_DELETE';

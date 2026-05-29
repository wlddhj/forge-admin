-- 工作流表单管理表
CREATE TABLE IF NOT EXISTS wf_form (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64) NOT NULL COMMENT '表单名称',
    status      TINYINT DEFAULT 0 COMMENT '状态(0正常 1停用)',
    conf        TEXT COMMENT '表单配置(JSON)',
    fields      TEXT COMMENT '表单字段列表(JSON数组)',
    remark      VARCHAR(255) COMMENT '备注',
    create_by   BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表单';

-- 流程部署扩展表增加表单关联字段
ALTER TABLE wf_process_deploy_ext ADD COLUMN form_type TINYINT DEFAULT NULL COMMENT '表单类型(10流程表单 20业务表单)' AFTER description;
ALTER TABLE wf_process_deploy_ext ADD COLUMN form_id BIGINT DEFAULT NULL COMMENT '关联表单ID' AFTER form_type;

-- 表单管理菜单（在流程管理目录下，菜单ID从200开始）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, status, create_time, update_time, create_by, update_by, deleted)
VALUES (200, (SELECT id FROM sys_menu WHERE menu_code = 'workflow' LIMIT 1), '表单管理', 1, '/workflow/form', 'workflow/form/index', 'workflow:form:list', 'edit', 3, 0, NOW(), NOW(), 1, 1, 0);

-- 表单管理按钮权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission, sort, status, create_time, update_time, create_by, update_by, deleted)
VALUES
(201, 200, '表单查询', 2, 'workflow:form:query', 1, 0, NOW(), NOW(), 1, 1, 0),
(202, 200, '表单新增', 2, 'workflow:form:add', 2, 0, NOW(), NOW(), 1, 1, 0),
(203, 200, '表单修改', 2, 'workflow:form:edit', 3, 0, NOW(), NOW(), 1, 1, 0),
(204, 200, '表单删除', 2, 'workflow:form:delete', 4, 0, NOW(), NOW(), 1, 1, 0);

-- 超级管理员角色关联新菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 200), (1, 201), (1, 202), (1, 203), (1, 204);

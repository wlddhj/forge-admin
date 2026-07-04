-- V202607041__create_screen_tables.sql
-- 大屏模块：4 张表 + SQL 白名单初始化（仅系统表，敏感列已排除）

-- 大屏主体
CREATE TABLE sys_screen (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code            VARCHAR(64)  NOT NULL                COMMENT '路由编码',
    name            VARCHAR(128) NOT NULL                COMMENT '显示名',
    description     VARCHAR(512)                         COMMENT '说明',
    config          JSON                                 COMMENT '已发布配置',
    config_draft    JSON                                 COMMENT '编辑中草稿',
    theme           VARCHAR(32)  DEFAULT 'dark-tech'     COMMENT '主题',
    status          TINYINT      DEFAULT 0               COMMENT '0=草稿 1=已发布',
    version         INT          DEFAULT 1               COMMENT '乐观锁',
    create_time     DATETIME     NOT NULL                COMMENT '创建时间',
    update_time     DATETIME     NOT NULL                COMMENT '更新时间',
    create_by       BIGINT                               COMMENT '创建人',
    update_by       BIGINT                               COMMENT '更新人',
    deleted         TINYINT      DEFAULT 0               COMMENT '0=未删 1=已删',
    remark          VARCHAR(255)                         COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_screen_code (code),
    KEY idx_status_code (status, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大屏主体';

-- 数据源（敏感配置）
CREATE TABLE sys_screen_data_source (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code            VARCHAR(64)  NOT NULL                COMMENT '数据源编码',
    name            VARCHAR(128) NOT NULL                COMMENT '数据源名称',
    type            VARCHAR(16)  NOT NULL                 COMMENT 'HTTP / SQL',
    config          JSON         NOT NULL                 COMMENT 'HTTP 或 SQL 配置',
    cache_seconds   INT          DEFAULT 0                COMMENT '缓存秒数',
    enabled         TINYINT      DEFAULT 1                COMMENT '0=禁用 1=启用',
    create_time     DATETIME     NOT NULL                 COMMENT '创建时间',
    update_time     DATETIME     NOT NULL                 COMMENT '更新时间',
    create_by       BIGINT                                COMMENT '创建人',
    update_by       BIGINT                                COMMENT '更新人',
    deleted         TINYINT      DEFAULT 0                COMMENT '0=未删 1=已删',
    remark          VARCHAR(255)                          COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_screen_ds_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大屏数据源';

-- 大屏与数据源关系
CREATE TABLE sys_screen_data_source_ref (
    screen_id       BIGINT NOT NULL COMMENT '大屏ID',
    data_source_id  BIGINT NOT NULL COMMENT '数据源ID',
    PRIMARY KEY (screen_id, data_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大屏数据源关系';

-- SQL 白名单（列级控制）
CREATE TABLE sys_screen_sql_whitelist (
    id           BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    schema_name  VARCHAR(64) NOT NULL               COMMENT '库名',
    table_name   VARCHAR(64) NOT NULL               COMMENT '表名',
    column_list  JSON COMMENT '允许的列，null=全部',
    risk_level   TINYINT COMMENT '0=公开 1=内部 2=敏感',
    enabled      TINYINT DEFAULT 1 COMMENT '0=禁用 1=启用',
    remark       VARCHAR(255) COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_whitelist_table (schema_name, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL 白名单';

-- 白名单初始化（仅系统表，敏感列已排除）
-- 列名以 sys_* 实际 schema 为准（已通过 DESC 校验）
-- 敏感列排除规则（spec §3.2/§6.2）：password、salt、email、phone、avatar、id_card、IP、PII
INSERT INTO sys_screen_sql_whitelist (schema_name, table_name, column_list, risk_level, remark) VALUES
('forge_admin', 'sys_user', JSON_ARRAY('id','dept_id','username','nickname','account_type','status','create_time','update_time'), 1, '用户表（排除 password/phone/email/avatar/last_login_ip/phone_suffix 等敏感列）'),
('forge_admin', 'sys_role', JSON_ARRAY('id','role_name','role_code','description','is_fixed','status','data_scope','sort_order','create_time'), 0, '角色表'),
('forge_admin', 'sys_dept', JSON_ARRAY('id','parent_id','dept_name','ancestors','leader','status','sort_order','create_time'), 0, '部门表（排除 email/phone）'),
('forge_admin', 'sys_menu', JSON_ARRAY('id','parent_id','menu_name','route_path','component_path','redirect_path','icon','sort_order','menu_type','permission','status','visible','is_external','is_cached','create_time'), 0, '菜单表'),
('forge_admin', 'sys_dict_type', JSON_ARRAY('id','dict_name','dict_type','status','is_system','remark','create_time'), 0, '字典类型表'),
('forge_admin', 'sys_login_log', JSON_ARRAY('id','username','login_location','browser','os','status','msg','login_time'), 1, '登录日志（排除 login_ip，IP 可识别个人）'),
('forge_admin', 'sys_operation_log', JSON_ARRAY('id','title','business_type','request_method','request_url','operator_id','operator_name','dept_name','status','operate_time','cost_time'), 1, '操作日志（排除 operate_ip/operate_location，IP 可识别个人）');

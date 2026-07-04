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

-- 白名单初始化（仅系统表，敏感列排除）
INSERT INTO sys_screen_sql_whitelist (schema_name, table_name, column_list, risk_level, remark) VALUES
('forge_admin', 'sys_user', JSON_ARRAY('id','dept_id','user_name','nick_name','status','create_time','update_time'), 1, '用户表（敏感列已排除）'),
('forge_admin', 'sys_role', JSON_ARRAY('id','role_name','role_key','status','data_scope','create_time'), 0, '角色表'),
('forge_admin', 'sys_dept', JSON_ARRAY('id','parent_id','dept_name','order_num','status','create_time'), 0, '部门表'),
('forge_admin', 'sys_menu', JSON_ARRAY('id','parent_id','menu_name','path','menu_type','visible','status','create_time'), 0, '菜单表'),
('forge_admin', 'sys_dict', JSON_ARRAY('id','dict_name','dict_type','status','create_time'), 0, '字典表'),
('forge_admin', 'sys_login_log', JSON_ARRAY('id','user_name','ipaddr','status','login_time'), 1, '登录日志'),
('forge_admin', 'sys_operation_log', JSON_ARRAY('id','title','business_type','method','request_url','status','oper_time'), 1, '操作日志');

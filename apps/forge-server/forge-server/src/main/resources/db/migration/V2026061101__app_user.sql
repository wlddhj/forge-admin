CREATE TABLE app_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    open_id         VARCHAR(64)  NOT NULL COMMENT '微信openid',
    union_id        VARCHAR(64)  DEFAULT NULL COMMENT '微信unionid',
    nickname        VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    avatar          VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态（0正常 1禁用）',
    last_login_time DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    UNIQUE KEY uk_open_id (open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='移动端用户表';

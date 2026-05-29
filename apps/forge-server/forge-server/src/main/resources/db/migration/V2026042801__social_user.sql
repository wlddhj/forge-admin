-- ========================================
-- 社交账号绑定表
-- ========================================
CREATE TABLE IF NOT EXISTS `sys_social_user` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '系统用户ID',
    `source` varchar(32) NOT NULL COMMENT '平台标识: wechat/dingtalk',
    `open_id` varchar(128) NOT NULL COMMENT '第三方open_id',
    `union_id` varchar(128) DEFAULT NULL COMMENT 'union_id(微信多应用绑定)',
    `access_token` varchar(512) DEFAULT NULL COMMENT '加密存储的access_token',
    `refresh_token` varchar(512) DEFAULT NULL COMMENT '加密存储的refresh_token',
    `token_expire_time` datetime DEFAULT NULL COMMENT 'token过期时间',
    `nickname` varchar(128) DEFAULT NULL COMMENT '第三方昵称',
    `avatar` varchar(512) DEFAULT NULL COMMENT '第三方头像URL',
    `raw_user_info` text DEFAULT NULL COMMENT '原始用户信息JSON',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
    `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:正常 1:已删除)',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_openid` (`source`, `open_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_union_id` (`union_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='社交账号绑定表';

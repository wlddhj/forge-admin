-- ========================================
-- 大屏角色授权表
-- ========================================
-- 用于大屏 access_type=1（指定角色可访问）场景。
-- 一条记录代表一个角色对一个 screen 的访问权限。

CREATE TABLE IF NOT EXISTS `sys_screen_role` (
    `screen_id` BIGINT NOT NULL COMMENT '大屏 ID',
    `role_id`   BIGINT NOT NULL COMMENT '角色 ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`screen_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大屏角色授权关系表';

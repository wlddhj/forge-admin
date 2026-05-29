-- 用户-岗位关联表
CREATE TABLE IF NOT EXISTS `sys_user_position` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `position_id` bigint NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_position` (`user_id`, `position_id`),
    KEY `idx_position_id` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户-岗位关联表';

-- 迁移旧数据：将 sys_user.position_id 迁移到关联表
INSERT INTO sys_user_position (user_id, position_id)
SELECT id, position_id FROM sys_user WHERE position_id IS NOT NULL AND deleted = 0;

-- 删除 sys_user 表的 position_id 字段（可选，保留兼容）
-- ALTER TABLE sys_user DROP COLUMN position_id;

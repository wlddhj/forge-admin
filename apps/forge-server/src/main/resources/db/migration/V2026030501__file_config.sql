-- 文件存储配置表
CREATE TABLE IF NOT EXISTS `sys_file_config` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_name` varchar(100) NOT NULL COMMENT '配置名称',
    `storage_type` varchar(20) NOT NULL COMMENT '存储类型(local/aliyun_oss/tencent_cos/minio)',
    `endpoint` varchar(255) DEFAULT NULL COMMENT '服务端点',
    `bucket_name` varchar(100) DEFAULT NULL COMMENT '存储桶名称',
    `access_key` varchar(100) DEFAULT NULL COMMENT 'AccessKey',
    `secret_key` varchar(100) DEFAULT NULL COMMENT 'SecretKey',
    `domain` varchar(255) DEFAULT NULL COMMENT '自定义域名',
    `base_path` varchar(255) DEFAULT NULL COMMENT '基础路径',
    `is_default` tinyint DEFAULT 0 COMMENT '是否默认(0:否 1:是)',
    `status` tinyint DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存储配置表';

-- 插入默认配置
INSERT INTO `sys_file_config` (`config_name`, `storage_type`, `base_path`, `is_default`, `status`, `remark`)
VALUES ('本地存储', 'local', '/uploads', 1, 1, '默认本地存储配置');

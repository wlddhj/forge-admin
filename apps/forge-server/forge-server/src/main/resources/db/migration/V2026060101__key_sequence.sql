-- 序列号生成规则表
CREATE TABLE IF NOT EXISTS `sys_key_sequence` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `key_category` varchar(64) NOT NULL COMMENT '分类编码',
    `key_prefix` varchar(64) DEFAULT NULL COMMENT '前缀（支持{0}占位符）',
    `date_rule` varchar(32) DEFAULT NULL COMMENT '日期格式规则（如 yyyyMMdd、yyyyMM、yyyy）',
    `max_value` bigint NOT NULL DEFAULT 0 COMMENT '当前最大值',
    `seq_length` int NOT NULL DEFAULT 4 COMMENT '顺序号位数',
    `last_date_val` varchar(32) DEFAULT NULL COMMENT '最近日期值',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    `deleted` int DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_key_category` (`key_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列号生成规则';

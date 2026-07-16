-- ========================================
-- forge-admin 数据库初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建时间: 2026-03-10
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================
-- 创建数据库
-- ========================================
CREATE DATABASE IF NOT EXISTS `forge_admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `forge_admin`;

-- ========================================
-- 1. 附件表
-- ========================================
DROP TABLE IF EXISTS `sys_attachment`;
CREATE TABLE `sys_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名称',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) NOT NULL COMMENT '文件路径',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `file_type` varchar(100) DEFAULT NULL COMMENT '文件类型(MIME类型)',
  `file_extension` varchar(50) DEFAULT NULL COMMENT '文件扩展名',
  `storage_type` varchar(50) DEFAULT 'local' COMMENT '存储类型(local/oss等)',
  `biz_type` varchar(100) DEFAULT NULL COMMENT '业务类型',
  `biz_id` bigint DEFAULT NULL COMMENT '业务ID',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传者ID',
  `uploader_name` varchar(100) DEFAULT NULL COMMENT '上传者名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_uploader` (`uploader_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统附件表';

-- ========================================
-- 2. 系统配置表
-- ========================================
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `config_type` varchar(50) DEFAULT 'text' COMMENT '配置类型',
  `config_group` varchar(50) DEFAULT 'system' COMMENT '配置分组',
  `is_system` tinyint DEFAULT '0' COMMENT '是否系统内置',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ========================================
-- 3. 部门表
-- ========================================
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dept_name` varchar(50) NOT NULL COMMENT '部门名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门ID',
  `ancestors` varchar(500) DEFAULT NULL COMMENT '祖级列表',
  `leader` varchar(50) DEFAULT NULL COMMENT '负责人',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '电话',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- ========================================
-- 4. 字典数据表
-- ========================================
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型',
  `dict_label` varchar(50) NOT NULL COMMENT '字典标签',
  `dict_value` varchar(100) NOT NULL COMMENT '字典值',
  `dict_sort` int DEFAULT '0' COMMENT '排序',
  `css_class` varchar(100) DEFAULT NULL COMMENT '样式属性',
  `list_class` varchar(100) DEFAULT NULL COMMENT '表格回显样式',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

-- ========================================
-- 5. 字典类型表
-- ========================================
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_name` varchar(50) NOT NULL COMMENT '字典名称',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `is_system` tinyint DEFAULT '0' COMMENT '是否系统内置',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- ========================================
-- 6. 文件存储配置表
-- ========================================
DROP TABLE IF EXISTS `sys_file_config`;
CREATE TABLE `sys_file_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `storage_type` varchar(20) NOT NULL COMMENT '存储类型(local/aliyun_oss/tencent_cos/minio)',
  `endpoint` varchar(255) DEFAULT NULL COMMENT '服务端点',
  `bucket_name` varchar(100) DEFAULT NULL COMMENT '存储桶名称',
  `access_key` varchar(100) DEFAULT NULL COMMENT 'AccessKey',
  `secret_key` varchar(100) DEFAULT NULL COMMENT 'SecretKey',
  `domain` varchar(255) DEFAULT NULL COMMENT '自定义域名',
  `base_path` varchar(255) DEFAULT NULL COMMENT '基础路径',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认(0:否 1:是)',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件存储配置表';

-- ========================================
-- 7. 定时任务表
-- ========================================
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) DEFAULT 'DEFAULT' COMMENT '任务分组',
  `invoke_target` varchar(500) NOT NULL COMMENT '调用目标',
  `cron_expression` varchar(255) NOT NULL COMMENT 'cron表达式',
  `status` tinyint DEFAULT '1' COMMENT '任务状态(0:暂停 1:正常)',
  `concurrent` tinyint DEFAULT '0' COMMENT '是否并发执行(0:禁止 1:允许)',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `timeout` int DEFAULT NULL COMMENT '超时时间(秒)',
  `retry_count` int DEFAULT 0 COMMENT '失败重试次数',
  `retry_interval` int DEFAULT 60 COMMENT '重试间隔(秒)',
  `notify_config` json DEFAULT NULL COMMENT '通知配置',
  `job_params` json DEFAULT NULL COMMENT '任务参数',
  `last_execute_at` datetime DEFAULT NULL COMMENT '最后执行时间',
  `last_execute_status` varchar(20) DEFAULT NULL COMMENT '最后执行状态(SUCCESS/FAIL/TIMEOUT)',
  `last_execute_duration` int DEFAULT NULL COMMENT '最后执行耗时(毫秒)',
  `total_execute_count` int DEFAULT 0 COMMENT '总执行次数',
  `success_count` int DEFAULT 0 COMMENT '成功次数',
  `failure_count` int DEFAULT 0 COMMENT '失败次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_group` (`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';

-- ========================================
-- 8. 任务执行日志表
-- ========================================
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` bigint NOT NULL COMMENT '任务ID',
  `job_name` varchar(64) NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) DEFAULT NULL COMMENT '任务分组',
  `invoke_target` varchar(500) NOT NULL COMMENT '调用目标',
  `job_message` varchar(500) DEFAULT NULL COMMENT '日志信息',
  `status` tinyint DEFAULT '0' COMMENT '执行状态（0:失败 1:成功）',
  `exception_info` text COMMENT '异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` bigint DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行日志表';

-- ========================================
-- 9. 登录日志表
-- ========================================
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `login_ip` varchar(128) DEFAULT NULL COMMENT '登录IP',
  `login_location` varchar(255) DEFAULT NULL COMMENT '登录地点',
  `browser` varchar(100) DEFAULT NULL COMMENT '浏览器',
  `os` varchar(100) DEFAULT NULL COMMENT '操作系统',
  `status` tinyint DEFAULT '1' COMMENT '登录状态(0:失败 1:成功)',
  `msg` varchar(255) DEFAULT NULL COMMENT '提示消息',
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- ========================================
-- 10. 菜单表
-- ========================================
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `route_path` varchar(200) DEFAULT NULL COMMENT '路由路径',
  `component_path` varchar(200) DEFAULT NULL COMMENT '组件路径',
  `redirect_path` varchar(200) DEFAULT NULL COMMENT '重定向路径',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `menu_type` tinyint DEFAULT '0' COMMENT '类型(0:目录 1:菜单 2:按钮)',
  `permission` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `visible` tinyint DEFAULT '1' COMMENT '是否可见(0:否 1:是)',
  `is_external` tinyint DEFAULT '0' COMMENT '是否外链',
  `is_cached` tinyint DEFAULT '0' COMMENT '是否缓存',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单表';

-- ========================================
-- 11. 通知公告表
-- ========================================
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notice_title` varchar(100) NOT NULL COMMENT '公告标题',
  `notice_type` tinyint NOT NULL DEFAULT '1' COMMENT '公告类型(1:通知 2:公告)',
  `notice_content` text COMMENT '公告内容',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:关闭 1:正常)',
  `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_notice_type` (`notice_type`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知公告表';

-- ========================================
-- 12. 操作日志表
-- ========================================
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(100) DEFAULT NULL COMMENT '操作标题',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方式',
  `request_url` varchar(500) DEFAULT NULL COMMENT '请求URL',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `dept_name` varchar(100) DEFAULT NULL COMMENT '部门名称',
  `operate_ip` varchar(50) DEFAULT NULL COMMENT '操作IP',
  `operate_location` varchar(100) DEFAULT NULL COMMENT '操作地点',
  `request_param` text COMMENT '请求参数',
  `json_result` text COMMENT '响应结果',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:失败 1:成功)',
  `error_msg` text COMMENT '错误信息',
  `operate_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '耗时(毫秒)',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_operate_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ========================================
-- 13. 岗位表
-- ========================================
DROP TABLE IF EXISTS `sys_position`;
CREATE TABLE `sys_position` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `position_name` varchar(50) NOT NULL COMMENT '岗位名称',
  `position_code` varchar(50) NOT NULL COMMENT '岗位编码',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_code` (`position_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位表';

-- ========================================
-- 14. 角色表
-- ========================================
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `is_fixed` tinyint DEFAULT '0' COMMENT '是否固定(0:否 1:是)',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `data_scope` varchar(64) DEFAULT '5' COMMENT '数据范围（1:全部 2:自定义 3:本部门 4:本部门及以下 5:仅本人）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ========================================
-- 15. 角色部门关联表
-- ========================================
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_dept` (`role_id`,`dept_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色部门关联表';

-- ========================================
-- 16. 角色菜单关联表
-- ========================================
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- ========================================
-- 17. 用户表
-- ========================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `phone` varchar(255) DEFAULT NULL COMMENT '手机号（加密存储）',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱（加密存储）',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `position_id` bigint DEFAULT NULL COMMENT '岗位ID',
  `account_type` tinyint DEFAULT '0' COMMENT '账户类型(0:普通用户 1:管理员)',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `password_update_time` datetime DEFAULT NULL COMMENT '密码最后修改时间',
  `first_login` tinyint NOT NULL DEFAULT 0 COMMENT '是否首次登录需强制改密(0:否 1:是)',
  `password_error_count` int NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `lock_time` datetime DEFAULT NULL COMMENT '账号锁定截止时间',
  `phone_suffix` varchar(4) DEFAULT NULL COMMENT '手机号后4位（明文，用于精确查询）',
  `email_suffix` varchar(64) DEFAULT NULL COMMENT '邮箱加盐SHA-256哈希（拖库安全，支持精确查询）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_status` (`status`),
  KEY `idx_lock_time` (`lock_time`),
  KEY `idx_email_suffix` (`email_suffix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ========================================
-- 18. 用户角色关联表
-- ========================================
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ========================================
-- 19. 用户岗位关联表
-- ========================================
DROP TABLE IF EXISTS `sys_user_position`;
CREATE TABLE `sys_user_position` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `position_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_position` (`user_id`,`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户岗位关联表';

-- ========================================
-- 19.1 用户密码历史表（等保二级：密码不可与最近N条历史重复）
-- ========================================
DROP TABLE IF EXISTS `sys_user_password_history`;
CREATE TABLE `sys_user_password_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `password` varchar(100) NOT NULL COMMENT 'BCrypt 哈希',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_create_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户密码历史';

-- ========================================
-- 20. 社交账号绑定表
-- ========================================
DROP TABLE IF EXISTS `sys_social_user`;
CREATE TABLE `sys_social_user` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交账号绑定表';

-- ========================================
-- 21. 移动端用户表
-- ========================================
DROP TABLE IF EXISTS `app_user`;
CREATE TABLE `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `open_id` varchar(64) NOT NULL COMMENT '微信openid',
  `union_id` varchar(64) DEFAULT NULL COMMENT '微信unionid',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(512) DEFAULT NULL COMMENT '头像URL',
  `phone` varchar(255) DEFAULT NULL COMMENT '手机号',
  `phone_verified` tinyint DEFAULT 0 COMMENT '手机号是否验证(0:未验证 1:已验证)',
  `deactivated_time` datetime DEFAULT NULL COMMENT '注销时间',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:禁用 1:正常)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:正常 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_open_id` (`open_id`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='移动端用户表';

-- ========================================
-- 22. OAuth2 注册客户端表
-- ========================================
DROP TABLE IF EXISTS `oauth2_registered_client`;
CREATE TABLE `oauth2_registered_client` (
  `id` varchar(100) NOT NULL,
  `client_id` varchar(100) NOT NULL,
  `client_id_issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `client_secret` varchar(200) DEFAULT NULL,
  `client_secret_expires_at` timestamp DEFAULT NULL,
  `client_name` varchar(200) NOT NULL,
  `client_authentication_methods` varchar(1000) NOT NULL,
  `authorization_grant_types` varchar(1000) NOT NULL,
  `redirect_uris` varchar(1000) DEFAULT NULL,
  `post_logout_redirect_uris` varchar(1000) DEFAULT NULL,
  `scopes` varchar(1000) NOT NULL,
  `client_settings` varchar(2000) NOT NULL,
  `token_settings` varchar(2000) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2注册客户端';

-- ========================================
-- 23. OAuth2 授权信息表
-- ========================================
DROP TABLE IF EXISTS `oauth2_authorization`;
CREATE TABLE `oauth2_authorization` (
  `id` varchar(100) NOT NULL,
  `registered_client_id` varchar(100) NOT NULL,
  `principal_name` varchar(200) NOT NULL,
  `authorization_grant_type` varchar(100) NOT NULL,
  `authorized_scopes` varchar(1000) DEFAULT NULL,
  `attributes` blob DEFAULT NULL,
  `state` varchar(500) DEFAULT NULL,
  `authorization_code_value` blob DEFAULT NULL,
  `authorization_code_issued_at` timestamp DEFAULT NULL,
  `authorization_code_expires_at` timestamp DEFAULT NULL,
  `authorization_code_metadata` blob DEFAULT NULL,
  `access_token_value` blob DEFAULT NULL,
  `access_token_issued_at` timestamp DEFAULT NULL,
  `access_token_expires_at` timestamp DEFAULT NULL,
  `access_token_metadata` blob DEFAULT NULL,
  `access_token_type` varchar(100) DEFAULT NULL,
  `access_token_scopes` varchar(1000) DEFAULT NULL,
  `oidc_id_token_value` blob DEFAULT NULL,
  `oidc_id_token_issued_at` timestamp DEFAULT NULL,
  `oidc_id_token_expires_at` timestamp DEFAULT NULL,
  `oidc_id_token_metadata` blob DEFAULT NULL,
  `refresh_token_value` blob DEFAULT NULL,
  `refresh_token_issued_at` timestamp DEFAULT NULL,
  `refresh_token_expires_at` timestamp DEFAULT NULL,
  `refresh_token_metadata` blob DEFAULT NULL,
  `user_code_value` blob DEFAULT NULL,
  `user_code_issued_at` timestamp DEFAULT NULL,
  `user_code_expires_at` timestamp DEFAULT NULL,
  `user_code_metadata` blob DEFAULT NULL,
  `device_code_value` blob DEFAULT NULL,
  `device_code_issued_at` timestamp DEFAULT NULL,
  `device_code_expires_at` timestamp DEFAULT NULL,
  `device_code_metadata` blob DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_registered_client_id` (`registered_client_id`),
  KEY `idx_principal_name` (`principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2授权信息';

-- ========================================
-- 24. OAuth2 授权同意表
-- ========================================
DROP TABLE IF EXISTS `oauth2_authorization_consent`;
CREATE TABLE `oauth2_authorization_consent` (
  `registered_client_id` varchar(100) NOT NULL,
  `principal_name` varchar(200) NOT NULL,
  `authorities` varchar(1000) NOT NULL,
  PRIMARY KEY (`registered_client_id`, `principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2授权同意';

-- ========================================
-- 25. 序列号生成规则表
-- ========================================
DROP TABLE IF EXISTS `sys_key_sequence`;
CREATE TABLE `sys_key_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `key_category` varchar(64) NOT NULL COMMENT '分类编码',
  `key_prefix` varchar(64) DEFAULT NULL COMMENT '前缀（支持{0}占位符）',
  `date_rule` varchar(32) DEFAULT NULL COMMENT '日期格式规则（如 yyyyMMdd、yyyyMM、yyyy）',
  `max_value` bigint NOT NULL DEFAULT 0 COMMENT '当前最大值',
  `seq_length` int NOT NULL DEFAULT 4 COMMENT '顺序号位数',
  `last_date_val` varchar(32) DEFAULT NULL COMMENT '最近日期值',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_category` (`key_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='序列号生成规则';

-- ========================================
-- 初始化数据
-- ========================================

-- 初始化部门
INSERT INTO `sys_dept` (`id`, `dept_name`, `parent_id`, `ancestors`, `leader`, `status`, `sort_order`) VALUES
(1, '总公司', 0, '0', 'admin', 1, 0),
(2, '研发部', 1, '0,1', '张三', 1, 1),
(3, '市场部', 1, '0,1', '李四', 1, 2),
(4, '财务部', 1, '0,1', '王五', 1, 3);

-- 初始化岗位
INSERT INTO `sys_position` (`id`, `position_name`, `position_code`, `sort_order`, `status`) VALUES
(1, '总经理', 'CEO', 1, 1),
(2, '技术总监', 'CTO', 2, 1),
(3, '开发工程师', 'DEV', 3, 1),
(4, '测试工程师', 'QA', 4, 1);

-- 初始化用户 (密码: password)
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `password`, `phone`, `email`, `dept_id`, `account_type`, `status`, `password_update_time`) VALUES
(1, 'admin', '超级管理员', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '13800000000', 'admin@standadmin.com', 1, 1, 1, NOW());

-- 现有用户标记 password_update_time = NOW()，避免立即触发密码过期
UPDATE `sys_user` SET `password_update_time` = NOW() WHERE `password_update_time` IS NULL;

-- 初始化角色
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `is_fixed`, `status`, `data_scope`, `sort_order`) VALUES
(1, '超级管理员', 'SUPER_ADMIN', '拥有所有权限', 1, 1, '1', 1),
(2, '普通用户', 'USER', '普通用户角色', 1, 1, '5', 2);

-- 初始化用户角色关联
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 初始化菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
-- 系统管理
(1, '系统管理', 0, '/system', 'Layout', NULL, 'Setting', 1, 0, NULL, 1, 1, 0, 0),
(2, '用户管理', 1, '/system/user', '/views/system/user/index', NULL, 'User', 1, 1, 'system:user:list', 1, 1, 0, 0),
(3, '角色管理', 1, '/system/role', '/views/system/role/index', NULL, 'UserFilled', 2, 1, 'system:role:list', 1, 1, 0, 0),
(4, '菜单管理', 1, '/system/menu', '/views/system/menu/index', NULL, 'Menu', 3, 1, 'system:menu:list', 1, 1, 0, 0),
(5, '部门管理', 1, '/system/dept', '/views/system/dept/index', NULL, 'OfficeBuilding', 4, 1, 'system:dept:list', 1, 1, 0, 0),
(6, '岗位管理', 1, '/system/position', '/views/system/position/index', NULL, 'Briefcase', 5, 1, 'system:position:list', 1, 1, 0, 0),
(7, '字典管理', 1, '/system/dict-type', '/views/system/dict-type/index', NULL, 'Collection', 6, 1, 'system:dict:list', 1, 1, 0, 0),
(8, '系统配置', 1, '/system/config', '/views/system/config/index', NULL, 'Tools', 7, 1, 'system:config:list', 1, 1, 0, 0),
-- 基础设施
(65, '基础设施', 0, '/basic', 'Layout', NULL, 'Platform', 2, 0, NULL, 1, 1, 0, 0),
(9, '操作日志', 65, '/system/operation-log', '/views/system/operation-log/index', NULL, 'Document', 1, 1, 'system:log:list', 1, 1, 0, 0),
(39, '定时任务', 65, '/system/job', '/views/system/job/index', NULL, 'Timer', 2, 1, 'system:job:list', 1, 1, 0, 0),
(44, '登录日志', 65, '/system/login-log', '/views/system/login-log/index', NULL, 'Promotion', 3, 1, 'system:login-log:list', 1, 1, 0, 0),
(47, '在线用户', 65, '/system/online-user', '/views/system/online-user/index', NULL, 'UserFilled', 4, 1, 'system:online-user:list', 1, 1, 0, 0),
(49, '通知公告', 65, '/system/notice', '/views/system/notice/index', NULL, 'BellFilled', 5, 1, 'system:notice:list', 1, 1, 0, 0),
(54, '文件配置', 65, '/system/file-config', '/views/system/file-config/index', NULL, 'FolderOpened', 6, 1, 'system:file-config:list', 1, 1, 0, 0),
(66, '附件管理', 65, '/system/attachment', '/views/system/attachment/index', NULL, 'Upload', 7, 1, 'system:attachment:list', 1, 1, 0, 0),
(80, '序列号生成器', 65, '/system/key-sequence', '/views/system/key-sequence/index', NULL, 'Stamp', 8, 1, 'system:key-sequence:list', 1, 1, 0, 0),
(70, 'OAuth2客户端', 1, '/system/oauth2-client', '/views/system/oauth2-client/index', NULL, 'Key', 8, 1, 'system:oauth2-client:list', 1, 1, 0, 0),
-- 用户管理按钮
(10, '用户查询', 2, '', '', NULL, '', 1, 2, 'system:user:query', 1, 1, 0, 0),
(11, '用户新增', 2, '', '', NULL, '', 2, 2, 'system:user:add', 1, 1, 0, 0),
(12, '用户编辑', 2, '', '', NULL, '', 3, 2, 'system:user:edit', 1, 1, 0, 0),
(13, '用户删除', 2, '', '', NULL, '', 4, 2, 'system:user:delete', 1, 1, 0, 0),
(14, '重置密码', 2, '', '', NULL, '', 5, 2, 'system:user:resetPwd', 1, 1, 0, 0),
(85, '解锁用户', 2, '', '', NULL, '', 6, 2, 'system:user:unlock', 1, 1, 0, 0),
(61, '用户导出', 2, '', '', NULL, '', 7, 2, 'system:user:export', 1, 1, 0, 0),
-- 角色管理按钮
(15, '角色查询', 3, '', '', NULL, '', 1, 2, 'system:role:query', 1, 1, 0, 0),
(16, '角色新增', 3, '', '', NULL, '', 2, 2, 'system:role:add', 1, 1, 0, 0),
(17, '角色编辑', 3, '', '', NULL, '', 3, 2, 'system:role:edit', 1, 1, 0, 0),
(18, '角色删除', 3, '', '', NULL, '', 4, 2, 'system:role:delete', 1, 1, 0, 0),
(62, '角色导出', 3, '', '', NULL, '', 5, 2, 'system:role:export', 1, 1, 0, 0),
-- 菜单管理按钮
(19, '菜单查询', 4, '', '', NULL, '', 1, 2, 'system:menu:query', 1, 1, 0, 0),
(20, '菜单新增', 4, '', '', NULL, '', 2, 2, 'system:menu:add', 1, 1, 0, 0),
(21, '菜单编辑', 4, '', '', NULL, '', 3, 2, 'system:menu:edit', 1, 1, 0, 0),
(22, '菜单删除', 4, '', '', NULL, '', 4, 2, 'system:menu:delete', 1, 1, 0, 0),
-- 部门管理按钮
(23, '部门查询', 5, '', '', NULL, '', 1, 2, 'system:dept:query', 1, 1, 0, 0),
(24, '部门新增', 5, '', '', NULL, '', 2, 2, 'system:dept:add', 1, 1, 0, 0),
(25, '部门编辑', 5, '', '', NULL, '', 3, 2, 'system:dept:edit', 1, 1, 0, 0),
(26, '部门删除', 5, '', '', NULL, '', 4, 2, 'system:dept:delete', 1, 1, 0, 0),
-- 岗位管理按钮
(27, '岗位查询', 6, '', '', NULL, '', 1, 2, 'system:position:query', 1, 1, 0, 0),
(28, '岗位新增', 6, '', '', NULL, '', 2, 2, 'system:position:add', 1, 1, 0, 0),
(29, '岗位编辑', 6, '', '', NULL, '', 3, 2, 'system:position:edit', 1, 1, 0, 0),
(30, '岗位删除', 6, '', '', NULL, '', 4, 2, 'system:position:delete', 1, 1, 0, 0),
-- 字典管理按钮
(31, '字典查询', 7, '', '', NULL, '', 1, 2, 'system:dict:query', 1, 1, 0, 0),
(32, '字典新增', 7, '', '', NULL, '', 2, 2, 'system:dict:add', 1, 1, 0, 0),
(33, '字典编辑', 7, '', '', NULL, '', 3, 2, 'system:dict:edit', 1, 1, 0, 0),
(34, '字典删除', 7, '', '', NULL, '', 4, 2, 'system:dict:delete', 1, 1, 0, 0),
-- 配置管理按钮
(35, '配置查询', 8, '', '', NULL, '', 1, 2, 'system:config:query', 1, 1, 0, 0),
(36, '配置新增', 8, '', '', NULL, '', 2, 2, 'system:config:add', 1, 1, 0, 0),
(37, '配置编辑', 8, '', '', NULL, '', 3, 2, 'system:config:edit', 1, 1, 0, 0),
(38, '配置删除', 8, '', '', NULL, '', 4, 2, 'system:config:delete', 1, 1, 0, 0),
-- 定时任务按钮
(40, '任务查询', 39, '', '', NULL, '', 1, 2, 'system:job:query', 1, 1, 0, 0),
(41, '任务新增', 39, '', '', NULL, '', 2, 2, 'system:job:add', 1, 1, 0, 0),
(42, '任务编辑', 39, '', '', NULL, '', 3, 2, 'system:job:edit', 1, 1, 0, 0),
(43, '任务删除', 39, '', '', NULL, '', 4, 2, 'system:job:delete', 1, 1, 0, 0),
(60, '任务日志', 39, '/system/job-log', '/views/system/job-log/index', NULL, 'List', 2, 1, 'monitor:job-log:list', 1, 0, 0, 0),
-- 登录日志按钮
(45, '日志查询', 44, NULL, NULL, NULL, NULL, 1, 2, 'system:login-log:query', 1, 1, 0, 0),
(46, '日志清空', 44, NULL, NULL, NULL, NULL, 2, 2, 'system:login-log:clear', 1, 1, 0, 0),
(64, '导出', 44, NULL, NULL, NULL, NULL, 3, 2, 'system:login-log:export', 1, 1, 0, 0),
-- 在线用户按钮
(48, '强制退出', 47, NULL, NULL, NULL, NULL, 1, 2, 'system:online-user:force-logout', 1, 1, 0, 0),
-- 通知公告按钮
(50, '公告查询', 49, NULL, NULL, NULL, NULL, 1, 2, 'system:notice:query', 1, 1, 0, 0),
(51, '公告新增', 49, NULL, NULL, NULL, NULL, 2, 2, 'system:notice:add', 1, 1, 0, 0),
(52, '公告编辑', 49, NULL, NULL, NULL, NULL, 3, 2, 'system:notice:edit', 1, 1, 0, 0),
(53, '公告删除', 49, NULL, NULL, NULL, NULL, 4, 2, 'system:notice:delete', 1, 1, 0, 0),
-- 文件配置按钮
(55, '配置查询', 54, NULL, NULL, NULL, NULL, 1, 2, 'system:file-config:query', 1, 1, 0, 0),
(56, '配置新增', 54, NULL, NULL, NULL, NULL, 2, 2, 'system:file-config:add', 1, 1, 0, 0),
(57, '配置编辑', 54, NULL, NULL, NULL, NULL, 3, 2, 'system:file-config:edit', 1, 1, 0, 0),
(58, '配置删除', 54, NULL, NULL, NULL, NULL, 4, 2, 'system:file-config:delete', 1, 1, 0, 0),
-- 操作日志导出
(63, '导出', 9, '', '', NULL, '', 3, 2, 'system:log:export', 1, 1, 0, 0),
-- 附件管理按钮
(67, '附件查询', 66, '', '', NULL, '', 1, 2, 'system:attachment:query', 1, 1, 0, 0),
(68, '附件上传', 66, '', '', NULL, '', 2, 2, 'system:attachment:upload', 1, 1, 0, 0),
(69, '附件删除', 66, '', '', NULL, '', 3, 2, 'system:attachment:delete', 1, 1, 0, 0),
-- OAuth2客户端按钮
(71, '客户端查询', 70, '', '', NULL, '', 1, 2, 'system:oauth2-client:query', 1, 1, 0, 0),
(72, '客户新增', 70, '', '', NULL, '', 2, 2, 'system:oauth2-client:add', 1, 1, 0, 0),
(73, '客户端修改', 70, '', '', NULL, '', 3, 2, 'system:oauth2-client:edit', 1, 1, 0, 0),
(74, '客户端删除', 70, '', '', NULL, '', 4, 2, 'system:oauth2-client:delete', 1, 1, 0, 0),
-- 序列号管理按钮
(81, '序列查询', 80, '', '', NULL, '', 1, 2, 'system:key-sequence:query', 1, 1, 0, 0),
(82, '序列新增', 80, '', '', NULL, '', 2, 2, 'system:key-sequence:add', 1, 1, 0, 0),
(83, '序列编辑', 80, '', '', NULL, '', 3, 2, 'system:key-sequence:edit', 1, 1, 0, 0),
(84, '序列删除', 80, '', '', NULL, '', 4, 2, 'system:key-sequence:delete', 1, 1, 0, 0);

-- 初始化角色菜单关联 (超级管理员拥有所有菜单)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu`;

-- 初始化字典类型
INSERT INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
(1, '用户性别', 'sys_user_sex', 1, 1, '用户性别字典'),
(2, '菜单状态', 'sys_show_hide', 1, 1, '菜单状态字典'),
(3, '系统开关', 'sys_normal_disable', 1, 1, '系统开关字典'),
(4, '任务状态', 'sys_job_status', 1, 1, '任务状态字典'),
(5, '任务分组', 'sys_job_group', 1, 1, '任务分组字典'),
(6, '通知类型', 'sys_notice_type', 1, 1, '通知类型字典'),
(7, '通知状态', 'sys_notice_status', 1, 1, '通知状态字典'),
(8, '操作类型', 'sys_oper_type', 1, 1, '操作类型字典'),
(9, '登录状态', 'sys_common_status', 1, 1, '登录状态字典'),
(10, '数据权限', 'sys_data_scope', 1, 1, '数据权限范围'),
(11, '参数类型', 'sys_config_type', 1, 1, '配置参数类型'),
(12, '标签样式', 'sys_tag_type', 1, 1, '表格标签样式'),
(13, '存储类型', 'sys_storage_type', 1, 1, '文件存储类型');

-- 初始化字典数据
INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `list_class`, `status`) VALUES
(1, 'sys_user_sex', '男', '0', 1, 'primary', 1),
(2, 'sys_user_sex', '女', '1', 2, 'danger', 1),
(3, 'sys_user_sex', '未知', '2', 3, 'info', 1),
(4, 'sys_show_hide', '显示', '0', 1, 'primary', 1),
(5, 'sys_show_hide', '隐藏', '1', 2, 'danger', 1),
(6, 'sys_normal_disable', '启用', '1', 1, 'success', 1),
(7, 'sys_normal_disable', '禁用', '0', 2, 'danger', 1),
(8, 'sys_oper_type', '其他', 'OTHER', 1, 'info', 1),
(9, 'sys_oper_type', '新增', 'INSERT', 2, 'info', 1),
(10, 'sys_oper_type', '修改', 'UPDATE', 3, 'info', 1),
(11, 'sys_oper_type', '删除', 'DELETE', 4, 'danger', 1),
(12, 'sys_oper_type', '授权', 'GRANT', 5, 'primary', 1),
(13, 'sys_oper_type', '导出', 'EXPORT', 6, 'warning', 1),
(14, 'sys_oper_type', '导入', 'IMPORT', 7, 'warning', 1),
(15, 'sys_common_status', '成功', '0', 1, 'primary', 1),
(16, 'sys_common_status', '失败', '1', 2, 'danger', 1),
-- 数据权限范围
(17, 'sys_data_scope', '全部数据权限', '1', 1, 'primary', 1),
(18, 'sys_data_scope', '自定义数据权限', '2', 2, 'success', 1),
(19, 'sys_data_scope', '本部门数据权限', '3', 3, 'info', 1),
(20, 'sys_data_scope', '本部门及以下数据权限', '4', 4, 'warning', 1),
(21, 'sys_data_scope', '仅本人数据权限', '5', 5, 'danger', 1),
-- 配置参数类型
(22, 'sys_config_type', '文本', 'text', 1, 'primary', 1),
(23, 'sys_config_type', '数字', 'number', 2, 'success', 1),
(24, 'sys_config_type', '布尔', 'boolean', 3, 'info', 1),
(25, 'sys_config_type', 'JSON', 'json', 4, 'warning', 1),
-- 标签样式
(26, 'sys_tag_type', '默认', 'default', 1, 'info', 1),
(27, 'sys_tag_type', '主要', 'primary', 2, 'primary', 1),
(28, 'sys_tag_type', '成功', 'success', 3, 'success', 1),
(29, 'sys_tag_type', '警告', 'warning', 4, 'warning', 1),
(30, 'sys_tag_type', '危险', 'danger', 5, 'danger', 1),
(31, 'sys_tag_type', '信息', 'info', 6, 'info', 1),
-- 文件存储类型
(32, 'sys_storage_type', '本地存储', 'local', 1, 'primary', 1),
(33, 'sys_storage_type', '阿里云OSS', 'aliyun_oss', 2, 'success', 1),
(34, 'sys_storage_type', '腾讯云COS', 'tencent_cos', 3, 'warning', 1),
(35, 'sys_storage_type', 'MinIO', 'minio', 4, 'info', 1),
	-- 通知类型
	(36, 'sys_notice_type', '通知', '1', 1, 'primary', 1),
	(37, 'sys_notice_type', '公告', '2', 2, 'success', 1),
	-- 通知状态
	(38, 'sys_notice_status', '关闭', '0', 1, 'danger', 1),
	(39, 'sys_notice_status', '正常', '1', 2, 'success', 1);

-- 初始化系统配置
INSERT INTO `sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`) VALUES
(1, '系统名称', 'sys.system.name', 'forge-admin', 'text', 'system', 1, 1, '系统名称'),
(2, '系统版本', 'sys.system.version', '1.0.0', 'text', 'system', 1, 1, '系统版本'),
(3, '文件上传路径', 'sys.upload.path', '/tmp/upload', 'text', 'system', 0, 1, '文件上传路径'),
(4, '验证码开关', 'sys.captcha.enabled', 'true', 'boolean', 'security', 1, 1, '验证码开关'),
(5, '密码最小长度', 'sys.password.minLength', '6', 'number', 'security', 1, 1, '密码最小长度');

-- 初始化文件存储配置
INSERT INTO `sys_file_config` (`id`, `config_name`, `storage_type`, `base_path`, `domain`, `is_default`, `status`, `remark`) VALUES
(1, '本地存储', 'local', '/uploads', 'http://localhost:8181/api/uploads', 1, 1, '默认本地存储配置');

-- 初始化定时任务
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `status`, `concurrent`, `remark`) VALUES
(1, '系统默认任务', 'DEFAULT', 'demoTask.execute(\"系统监控\")', '0 0 0 * * ?', 0, 0, '系统默认示例任务(暂停状态)'),
(2, '日志清理任务', 'SYSTEM', 'demoTask.cleanExpiredData()', '0 0 2 * * ?', 1, 0, '每天凌晨2点清理过期日志');

-- 初始化通知公告
INSERT INTO `sys_notice` (`id`, `notice_title`, `notice_type`, `notice_content`, `status`, `create_by`, `remark`) VALUES
(1, '欢迎使用forge-admin管理系统', 2, 'forge-admin是一个基于Spring Boot 3和Vue 3的企业级后台管理系统，提供了用户管理、角色管理、菜单管理、部门管理等常用功能模块。', 1, 1, '系统欢迎公告'),
(2, '系统升级通知', 1, '系统将于本周六凌晨2点进行版本升级，届时系统将暂停服务约30分钟，请提前做好相关工作安排。', 1, 1, '维护通知');

-- 初始化序列号种子数据
INSERT INTO `sys_key_sequence` (`key_category`, `key_prefix`, `date_rule`, `seq_length`, `remark`) VALUES
('process_no', 'WF', 'yyyyMMdd', 4, '流程编号');

-- ========================================
-- 多租户支持（V2026071101 起）
-- ========================================

-- 1) 业务表加 tenant_id 字段（跨租户共享表不加：sys_menu/sys_dict_*/sys_config/sys_file_config/sys_job/sys_role/sys_role_menu）
ALTER TABLE `sys_attachment`        ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_dept`              ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_login_log`         ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_notice`            ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_operation_log`     ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_position`          ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_role_dept`         ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE `sys_user`              ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);
ALTER TABLE `sys_user_role`         ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE `sys_user_position`     ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE `sys_user_password_history` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE `sys_job_log`           ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE `app_user`              ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER `id`, ADD INDEX `idx_tenant` (`tenant_id`);

-- 2) sys_user 用户名唯一索引改为 (tenant_id, username) 联合唯一（允许跨租户重名）
ALTER TABLE `sys_user` DROP INDEX `uk_username`;
ALTER TABLE `sys_user` ADD UNIQUE INDEX `uk_tenant_username` (`tenant_id`, `username`);

-- 3) 创建租户主表与套餐表
CREATE TABLE IF NOT EXISTS `sys_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '租户名称',
  `code` varchar(32) NOT NULL COMMENT '租户标识（登录用）',
  `contact_name` varchar(32) DEFAULT NULL,
  `contact_phone` varchar(32) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `package_id` bigint DEFAULT NULL COMMENT '套餐ID',
  `expire_time` datetime DEFAULT NULL COMMENT '到期时间（NULL=永久）',
  `website` varchar(255) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`),
  KEY `idx_package` (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

CREATE TABLE IF NOT EXISTS `sys_tenant_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `code` varchar(32) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐';

CREATE TABLE IF NOT EXISTS `sys_tenant_package_menu` (
  `tenant_package_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_package_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐-菜单关联';

CREATE TABLE IF NOT EXISTS `sys_tenant_package_role` (
  `tenant_package_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_package_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐-角色关联';

-- 4) 初始化默认租户、默认套餐
INSERT IGNORE INTO `sys_tenant` (`id`, `name`, `code`, `status`, `package_id`, `remark`) VALUES
(1, '默认租户', 'default', 1, 1, '系统初始租户');

INSERT IGNORE INTO `sys_tenant_package` (`id`, `name`, `code`, `status`, `remark`) VALUES
(1, '默认套餐', 'default', 1, '包含全部菜单');

-- 5) admin 升级为平台超管（account_type=2 跨租户）
UPDATE `sys_user` SET `account_type` = 2 WHERE `username` = 'admin' AND `tenant_id` = 1;

-- 6) 新增"租户管理员"角色（区别于平台超管，仅管本租户）
INSERT IGNORE INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `is_fixed`, `status`, `data_scope`, `sort_order`) VALUES
(3, '租户管理员', 'TENANT_ADMIN', '本租户内全权管理（用户/部门/角色等）', 1, 1, '1', 50);

-- 7) 给"租户管理员"分配菜单（排除平台专属：菜单管理/OAuth2/App用户/租户管理）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 3, `id` FROM `sys_menu`
WHERE `id` NOT IN (4, 70, 246, 2400, 2401, 2402, 2403, 2404, 2405, 2410, 2411, 2412, 2413, 2414)
  AND `deleted` = 0;

-- 8) 默认套餐绑定所有菜单
INSERT IGNORE INTO `sys_tenant_package_menu` (`tenant_package_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `deleted` = 0;

-- 9) 新增"租户管理"菜单（顶级在系统管理目录下，仅平台超管可见）
INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`, `create_time`, `update_time`, `deleted`) VALUES
(2400, '租户管理', 1, '/system/tenant',           'system/tenant/index',           'OfficeBuilding', 90, 1, 'system:tenant:list',           1, 1, 0, 0, NOW(), NOW(), 0),
(2410, '套餐管理', 1, '/system/tenant-package',   'system/tenant-package/index',   'Box',            91, 1, 'system:tenant-package:list',  1, 1, 0, 0, NOW(), NOW(), 0);

INSERT IGNORE INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `menu_type`, `permission`, `sort_order`, `status`, `visible`, `is_external`, `is_cached`, `create_time`, `update_time`, `deleted`) VALUES
(2401, '租户查询', 2400, 2, 'system:tenant:query',           1, 1, 1, 0, 0, NOW(), NOW(), 0),
(2402, '租户新增', 2400, 2, 'system:tenant:add',             2, 1, 1, 0, 0, NOW(), NOW(), 0),
(2403, '租户修改', 2400, 2, 'system:tenant:update',          3, 1, 1, 0, 0, NOW(), NOW(), 0),
(2404, '租户删除', 2400, 2, 'system:tenant:delete',          4, 1, 1, 0, 0, NOW(), NOW(), 0),
(2405, '状态切换', 2400, 2, 'system:tenant:status',          5, 1, 1, 0, 0, NOW(), NOW(), 0),
(2411, '套餐查询', 2410, 2, 'system:tenant-package:query',   1, 1, 1, 0, 0, NOW(), NOW(), 0),
(2412, '套餐新增', 2410, 2, 'system:tenant-package:add',     2, 1, 1, 0, 0, NOW(), NOW(), 0),
(2413, '套餐修改', 2410, 2, 'system:tenant-package:update',  3, 1, 1, 0, 0, NOW(), NOW(), 0),
(2414, '套餐删除', 2410, 2, 'system:tenant-package:delete',  4, 1, 1, 0, 0, NOW(), NOW(), 0);

-- 10) 把租户管理菜单分配给超级管理员（id=1）和默认套餐
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 2400), (1, 2401), (1, 2402), (1, 2403), (1, 2404), (1, 2405),
(1, 2410), (1, 2411), (1, 2412), (1, 2413), (1, 2414);

INSERT IGNORE INTO `sys_tenant_package_menu` (`tenant_package_id`, `menu_id`) VALUES
(1, 2400), (1, 2401), (1, 2402), (1, 2403), (1, 2404), (1, 2405),
(1, 2410), (1, 2411), (1, 2412), (1, 2413), (1, 2414);

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT '数据库初始化完成!' AS message;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统附件表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件存储配置表';

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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_group` (`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务执行日志表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

-- ========================================
-- 10. 菜单表
-- ========================================
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单名称',
  `menu_code` varchar(50) DEFAULT NULL COMMENT '菜单编码',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知公告表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色部门关联表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';

-- ========================================
-- 17. 用户表
-- ========================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `position_id` bigint DEFAULT NULL COMMENT '岗位ID',
  `account_type` tinyint DEFAULT '0' COMMENT '账户类型(0:普通用户 1:管理员)',
  `status` tinyint DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

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
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `password`, `phone`, `email`, `dept_id`, `account_type`, `status`) VALUES
(1, 'admin', '超级管理员', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '13800000000', 'admin@standadmin.com', 1, 1, 1);

-- 初始化角色
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `is_fixed`, `status`, `data_scope`, `sort_order`) VALUES
(1, '超级管理员', 'SUPER_ADMIN', '拥有所有权限', 1, 1, '1', 1),
(2, '普通用户', 'USER', '普通用户角色', 1, 1, '5', 2);

-- 初始化用户角色关联
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 初始化菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `menu_code`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(1, '系统管理', NULL, 0, '/system', 'Layout', NULL, 'Setting', 1, 0, NULL, 1, 1, 0, 0),
(2, '用户管理', NULL, 1, '/system/user', '/views/system/user/index', NULL, 'User', 1, 1, 'system:user:list', 1, 1, 0, 0),
(3, '角色管理', NULL, 1, '/system/role', '/views/system/role/index', NULL, 'UserFilled', 2, 1, 'system:role:list', 1, 1, 0, 0),
(4, '菜单管理', NULL, 1, '/system/menu', '/views/system/menu/index', NULL, 'Menu', 3, 1, 'system:menu:list', 1, 1, 0, 0),
(5, '部门管理', NULL, 1, '/system/dept', '/views/system/dept/index', NULL, 'OfficeBuilding', 4, 1, 'system:dept:list', 1, 1, 0, 0),
(6, '岗位管理', NULL, 1, '/system/position', '/views/system/position/index', NULL, 'Briefcase', 5, 1, 'system:position:list', 1, 1, 0, 0),
(7, '字典管理', NULL, 1, '/system/dict-type', '/views/system/dict-type/index', NULL, 'Collection', 6, 1, 'system:dict:list', 1, 1, 0, 0),
(8, '系统配置', NULL, 1, '/system/config', '/views/system/config/index', NULL, 'Tools', 7, 1, 'system:config:list', 1, 1, 0, 0),
(9, '操作日志', NULL, 1, '/system/operation-log', '/views/system/operation-log/index', NULL, 'Document', 8, 1, 'system:log:list', 1, 1, 0, 0),
-- 用户管理按钮
(10, '用户查询', NULL, 2, '', '', NULL, '', 1, 2, 'system:user:query', 1, 1, 0, 0),
(11, '用户新增', NULL, 2, '', '', NULL, '', 2, 2, 'system:user:add', 1, 1, 0, 0),
(12, '用户编辑', NULL, 2, '', '', NULL, '', 3, 2, 'system:user:edit', 1, 1, 0, 0),
(13, '用户删除', NULL, 2, '', '', NULL, '', 4, 2, 'system:user:delete', 1, 1, 0, 0),
(14, '重置密码', NULL, 2, '', '', NULL, '', 5, 2, 'system:user:resetPwd', 1, 1, 0, 0),
-- 角色管理按钮
(15, '角色查询', NULL, 3, '', '', NULL, '', 1, 2, 'system:role:query', 1, 1, 0, 0),
(16, '角色新增', NULL, 3, '', '', NULL, '', 2, 2, 'system:role:add', 1, 1, 0, 0),
(17, '角色编辑', NULL, 3, '', '', NULL, '', 3, 2, 'system:role:edit', 1, 1, 0, 0),
(18, '角色删除', NULL, 3, '', '', NULL, '', 4, 2, 'system:role:delete', 1, 1, 0, 0),
-- 菜单管理按钮
(19, '菜单查询', NULL, 4, '', '', NULL, '', 1, 2, 'system:menu:query', 1, 1, 0, 0),
(20, '菜单新增', NULL, 4, '', '', NULL, '', 2, 2, 'system:menu:add', 1, 1, 0, 0),
(21, '菜单编辑', NULL, 4, '', '', NULL, '', 3, 2, 'system:menu:edit', 1, 1, 0, 0),
(22, '菜单删除', NULL, 4, '', '', NULL, '', 4, 2, 'system:menu:delete', 1, 1, 0, 0),
-- 部门管理按钮
(23, '部门查询', NULL, 5, '', '', NULL, '', 1, 2, 'system:dept:query', 1, 1, 0, 0),
(24, '部门新增', NULL, 5, '', '', NULL, '', 2, 2, 'system:dept:add', 1, 1, 0, 0),
(25, '部门编辑', NULL, 5, '', '', NULL, '', 3, 2, 'system:dept:edit', 1, 1, 0, 0),
(26, '部门删除', NULL, 5, '', '', NULL, '', 4, 2, 'system:dept:delete', 1, 1, 0, 0),
-- 岗位管理按钮
(27, '岗位查询', 'system:position:query', 6, '', '', NULL, '', 1, 2, 'system:position:query', 1, 1, 0, 0),
(28, '岗位新增', 'system:position:add', 6, '', '', NULL, '', 2, 2, 'system:position:add', 1, 1, 0, 0),
(29, '岗位编辑', 'system:position:edit', 6, '', '', NULL, '', 3, 2, 'system:position:edit', 1, 1, 0, 0),
(30, '岗位删除', 'system:position:delete', 6, '', '', NULL, '', 4, 2, 'system:position:delete', 1, 1, 0, 0),
-- 字典管理按钮
(31, '字典查询', 'system:dict:query', 7, '', '', NULL, '', 1, 2, 'system:dict:query', 1, 1, 0, 0),
(32, '字典新增', 'system:dict:add', 7, '', '', NULL, '', 2, 2, 'system:dict:add', 1, 1, 0, 0),
(33, '字典编辑', 'system:dict:edit', 7, '', '', NULL, '', 3, 2, 'system:dict:edit', 1, 1, 0, 0),
(34, '字典删除', 'system:dict:delete', 7, '', '', NULL, '', 4, 2, 'system:dict:delete', 1, 1, 0, 0),
-- 配置管理按钮
(35, '配置查询', 'system:config:query', 8, '', '', NULL, '', 1, 2, 'system:config:query', 1, 1, 0, 0),
(36, '配置新增', 'system:config:add', 8, '', '', NULL, '', 2, 2, 'system:config:add', 1, 1, 0, 0),
(37, '配置编辑', 'system:config:edit', 8, '', '', NULL, '', 3, 2, 'system:config:edit', 1, 1, 0, 0),
(38, '配置删除', 'system:config:delete', 8, '', '', NULL, '', 4, 2, 'system:config:delete', 1, 1, 0, 0),
-- 定时任务
(39, '定时任务', 'system:job:list', 1, '/system/job', '/views/system/job/index', '', 'Timer', 10, 1, 'system:job:list', 1, 1, 0, 0),
(40, '任务查询', 'system:job:query', 39, '', '', NULL, '', 1, 2, 'system:job:query', 1, 1, 0, 0),
(41, '任务新增', 'system:job:add', 39, '', '', NULL, '', 2, 2, 'system:job:add', 1, 1, 0, 0),
(42, '任务编辑', 'system:job:edit', 39, '', '', NULL, '', 3, 2, 'system:job:edit', 1, 1, 0, 0),
(43, '任务删除', 'system:job:delete', 39, '', '', NULL, '', 4, 2, 'system:job:delete', 1, 1, 0, 0),
-- 登录日志
(44, '登录日志', 'system:login-log:list', 1, '/system/login-log', '/views/system/login-log/index', NULL, 'Promotion', 11, 1, 'system:login-log:list', 1, 1, 0, 0),
(45, '日志查询', 'system:login-log:query', 44, NULL, NULL, NULL, NULL, 1, 2, 'system:login-log:query', 1, 1, 0, 0),
(46, '日志清空', 'system:login-log:clear', 44, NULL, NULL, NULL, NULL, 2, 2, 'system:login-log:clear', 1, 1, 0, 0),
-- 在线用户
(47, '在线用户', 'system:online-user:list', 1, '/system/online-user', '/views/system/online-user/index', NULL, 'UserFilled', 12, 1, 'system:online-user:list', 1, 1, 0, 0),
(48, '强制退出', 'system:online-user:force-logout', 47, NULL, NULL, NULL, NULL, 1, 2, 'system:online-user:force-logout', 1, 1, 0, 0),
-- 通知公告
(49, '通知公告', 'system:notice:list', 1, '/system/notice', '/views/system/notice/index', NULL, 'BellFilled', 13, 1, 'system:notice:list', 1, 1, 0, 0),
(50, '公告查询', 'system:notice:query', 49, NULL, NULL, NULL, NULL, 1, 2, 'system:notice:query', 1, 1, 0, 0),
(51, '公告新增', 'system:notice:add', 49, NULL, NULL, NULL, NULL, 2, 2, 'system:notice:add', 1, 1, 0, 0),
(52, '公告编辑', 'system:notice:edit', 49, NULL, NULL, NULL, NULL, 3, 2, 'system:notice:edit', 1, 1, 0, 0),
(53, '公告删除', 'system:notice:delete', 49, NULL, NULL, NULL, NULL, 4, 2, 'system:notice:delete', 1, 1, 0, 0),
-- 文件配置
(54, '文件配置', 'system:file-config:list', 1, '/system/file-config', '/views/system/file-config/index', NULL, 'FolderOpened', 14, 1, 'system:file-config:list', 1, 1, 0, 0),
(55, '配置查询', 'system:file-config:query', 54, NULL, NULL, NULL, NULL, 1, 2, 'system:file-config:query', 1, 1, 0, 0),
(56, '配置新增', 'system:file-config:add', 54, NULL, NULL, NULL, NULL, 2, 2, 'system:file-config:add', 1, 1, 0, 0),
(57, '配置编辑', 'system:file-config:edit', 54, NULL, NULL, NULL, NULL, 3, 2, 'system:file-config:edit', 1, 1, 0, 0),
(58, '配置删除', 'system:file-config:delete', 54, NULL, NULL, NULL, NULL, 4, 2, 'system:file-config:delete', 1, 1, 0, 0);

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
(35, 'sys_storage_type', 'MinIO', 'minio', 4, 'info', 1);

-- 初始化系统配置
INSERT INTO `sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`) VALUES
(1, '系统名称', 'sys.system.name', 'forge-admin', 'text', 'system', 1, 1, '系统名称'),
(2, '系统版本', 'sys.system.version', '1.0.0', 'text', 'system', 1, 1, '系统版本'),
(3, '文件上传路径', 'sys.upload.path', '/tmp/upload', 'text', 'system', 0, 1, '文件上传路径'),
(4, '验证码开关', 'sys.captcha.enabled', 'true', 'boolean', 'security', 1, 1, '验证码开关'),
(5, '密码最小长度', 'sys.password.minLength', '6', 'number', 'security', 1, 1, '密码最小长度');

-- 初始化文件存储配置
INSERT INTO `sys_file_config` (`id`, `config_name`, `storage_type`, `base_path`, `is_default`, `status`, `remark`) VALUES
(1, '本地存储', 'local', '/uploads', 1, 1, '默认本地存储配置');

-- 初始化定时任务
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `status`, `concurrent`, `remark`) VALUES
(1, '系统默认任务', 'DEFAULT', 'demoTask.execute(\"系统监控\")', '0 0 0 * * ?', 0, 0, '系统默认示例任务(暂停状态)'),
(2, '日志清理任务', 'SYSTEM', 'demoTask.cleanExpiredData()', '0 0 2 * * ?', 1, 0, '每天凌晨2点清理过期日志');

-- 初始化通知公告
INSERT INTO `sys_notice` (`id`, `notice_title`, `notice_type`, `notice_content`, `status`, `create_by`, `remark`) VALUES
(1, '欢迎使用forge-admin管理系统', 2, 'forge-admin是一个基于Spring Boot 3和Vue 3的企业级后台管理系统，提供了用户管理、角色管理、菜单管理、部门管理等常用功能模块。', 1, 1, '系统欢迎公告'),
(2, '系统升级通知', 1, '系统将于本周六凌晨2点进行版本升级，届时系统将暂停服务约30分钟，请提前做好相关工作安排。', 1, 1, '维护通知');

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT '数据库初始化完成!' AS message;

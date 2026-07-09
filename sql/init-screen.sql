-- ========================================
-- forge-admin 大屏模块初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建时间: 2026-07-09
-- 说明: 本脚本包含大屏模块相关的表结构和数据
--       整合自 forge-module-screen-biz 的 6 个 Flyway 迁移脚本
-- 前置依赖: sql/init.sql (系统基础表)
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `forge_admin`;

-- ========================================
-- Part 1: 表结构（4 张）
-- ========================================

-- 1. 大屏主体
DROP TABLE IF EXISTS `sys_screen`;
CREATE TABLE `sys_screen` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`         VARCHAR(64)  NOT NULL                COMMENT '路由编码',
    `name`         VARCHAR(128) NOT NULL                COMMENT '显示名',
    `description`  VARCHAR(512)                         COMMENT '说明',
    `config`       JSON                                 COMMENT '已发布配置',
    `config_draft` JSON                                 COMMENT '编辑中草稿',
    `theme`        VARCHAR(32)  DEFAULT 'dark-tech'     COMMENT '主题',
    `status`       TINYINT      DEFAULT 0               COMMENT '0=草稿 1=已发布',
    `is_public`    INT          NOT NULL DEFAULT 0      COMMENT '是否公开：0=登录可访问 1=公开访问（无需登录）',
    `access_type`  INT          NOT NULL DEFAULT 0      COMMENT '访问授权类型：0=登录可访问 1=指定角色可访问',
    `version`      INT          DEFAULT 1               COMMENT '乐观锁',
    `create_time`  DATETIME     NOT NULL                COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL                COMMENT '更新时间',
    `create_by`    BIGINT                               COMMENT '创建人',
    `update_by`    BIGINT                               COMMENT '更新人',
    `deleted`      TINYINT      DEFAULT 0               COMMENT '0=未删 1=已删',
    `remark`       VARCHAR(255)                         COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_screen_code` (`code`),
    KEY `idx_status_code` (`status`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大屏主体';

-- 2. 数据源（敏感配置）
DROP TABLE IF EXISTS `sys_screen_data_source`;
CREATE TABLE `sys_screen_data_source` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`          VARCHAR(64)  NOT NULL                COMMENT '数据源编码',
    `name`          VARCHAR(128) NOT NULL                COMMENT '数据源名称',
    `type`          VARCHAR(16)  NOT NULL                COMMENT 'HTTP / SQL',
    `config`        JSON         NOT NULL                COMMENT 'HTTP 或 SQL 配置',
    `cache_seconds` INT          DEFAULT 0               COMMENT '缓存秒数',
    `enabled`       TINYINT      DEFAULT 1               COMMENT '0=禁用 1=启用',
    `create_time`   DATETIME     NOT NULL                COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL                COMMENT '更新时间',
    `create_by`     BIGINT                               COMMENT '创建人',
    `update_by`     BIGINT                               COMMENT '更新人',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '0=未删 1=已删',
    `remark`        VARCHAR(255)                         COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_screen_ds_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大屏数据源';

-- 3. SQL 白名单（列级控制）
DROP TABLE IF EXISTS `sys_screen_sql_whitelist`;
CREATE TABLE `sys_screen_sql_whitelist` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `schema_name` VARCHAR(64) NOT NULL             COMMENT '库名',
    `table_name`  VARCHAR(64) NOT NULL             COMMENT '表名',
    `column_list` JSON                            COMMENT '允许的列，null=全部',
    `risk_level`  TINYINT                         COMMENT '0=公开 1=内部 2=敏感',
    `enabled`     TINYINT DEFAULT 1               COMMENT '0=禁用 1=启用',
    `remark`      VARCHAR(255)                    COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_whitelist_table` (`schema_name`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL 白名单';

-- 4. 大屏角色授权表（access_type=1 时使用）
DROP TABLE IF EXISTS `sys_screen_role`;
CREATE TABLE `sys_screen_role` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `screen_id`   BIGINT NOT NULL                COMMENT '大屏 ID',
    `role_id`     BIGINT NOT NULL                COMMENT '角色 ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_screen_id` (`screen_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大屏角色授权关系表';

-- ========================================
-- Part 2: SQL 白名单初始化（仅系统表，敏感列已排除）
-- ========================================
-- 敏感列排除规则：password、salt、email、phone、avatar、id_card、IP、PII
-- column_list 为空或 null 表示 fail-closed（拒绝所有请求列），强制管理员显式声明允许的列

INSERT INTO `sys_screen_sql_whitelist` (`schema_name`, `table_name`, `column_list`, `risk_level`, `remark`) VALUES
('forge_admin', 'sys_user',         JSON_ARRAY('id','dept_id','username','nickname','account_type','status','create_time','update_time'), 1, '用户表（排除 password/phone/email/avatar/last_login_ip/phone_suffix 等敏感列）'),
('forge_admin', 'sys_role',         JSON_ARRAY('id','role_name','role_code','description','is_fixed','status','data_scope','sort_order','create_time'), 0, '角色表'),
('forge_admin', 'sys_dept',         JSON_ARRAY('id','parent_id','dept_name','ancestors','leader','status','sort_order','create_time'), 0, '部门表（排除 email/phone）'),
('forge_admin', 'sys_menu',         JSON_ARRAY('id','parent_id','menu_name','route_path','component_path','redirect_path','icon','sort_order','menu_type','permission','status','visible','is_external','is_cached','create_time'), 0, '菜单表'),
('forge_admin', 'sys_dict_type',    JSON_ARRAY('id','dict_name','dict_type','status','is_system','remark','create_time'), 0, '字典类型表'),
('forge_admin', 'sys_login_log',    JSON_ARRAY('id','username','login_location','browser','os','status','msg','login_time'), 1, '登录日志（排除 login_ip，IP 可识别个人）'),
('forge_admin', 'sys_operation_log',JSON_ARRAY('id','title','business_type','request_method','request_url','operator_id','operator_name','dept_name','status','operate_time','cost_time'), 1, '操作日志（排除 operate_ip/operate_location，IP 可识别个人）');

-- ========================================
-- Part 3: 菜单与权限种子数据
-- ========================================
-- ID 段 2300-2399，避开系统模块（<2000）、OAuth2(70-79)、AI(300-323)、工作流(2200+)
-- 注：screen:screen:view:{code} 为动态权限，由大屏实例创建时生成，不在本脚本中预置

-- 3.1 大屏管理目录（顶级目录，sort_order=10，位于工作流之后）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2300, '大屏管理', 0, '/screen', 'Layout', '/screen/index', 'Monitor', 10, 0, NULL, 1, 1, 0, 0);

-- 3.2 大屏列表菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2310, '大屏列表', 2300, '/screen/index', '/views/screen/index/index', NULL, 'Document', 1, 1, 'screen:screen:list', 1, 1, 0, 0);

-- 3.3 大屏管理按钮权限（list/query/add/edit/remove/publish/copy）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2311, '大屏查询', 2310, '', '', NULL, '', 1, 2, 'screen:screen:query',  1, 1, 0, 0),
(2312, '大屏新增', 2310, '', '', NULL, '', 2, 2, 'screen:screen:add',     1, 1, 0, 0),
(2313, '大屏编辑', 2310, '', '', NULL, '', 3, 2, 'screen:screen:edit',    1, 1, 0, 0),
(2314, '大屏删除', 2310, '', '', NULL, '', 4, 2, 'screen:screen:remove',  1, 1, 0, 0),
(2315, '大屏发布', 2310, '', '', NULL, '', 5, 2, 'screen:screen:publish', 1, 1, 0, 0),
(2316, '大屏复制', 2310, '', '', NULL, '', 6, 2, 'screen:screen:copy',    1, 1, 0, 0);

-- 3.4 数据源管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2320, '数据源管理', 2300, '/screen/data-source', '/views/screen/data-source/index', NULL, 'Connection', 2, 1, 'screen:data-source:list', 1, 1, 0, 0);

-- 3.5 数据源管理按钮权限（list/query/add/edit/remove/execute）
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2321, '数据源查询', 2320, '', '', NULL, '', 1, 2, 'screen:data-source:query',   1, 1, 0, 0),
(2322, '数据源新增', 2320, '', '', NULL, '', 2, 2, 'screen:data-source:add',     1, 1, 0, 0),
(2323, '数据源编辑', 2320, '', '', NULL, '', 3, 2, 'screen:data-source:edit',    1, 1, 0, 0),
(2324, '数据源删除', 2320, '', '', NULL, '', 4, 2, 'screen:data-source:remove',  1, 1, 0, 0),
(2325, '数据源执行', 2320, '', '', NULL, '', 5, 2, 'screen:data-source:execute', 1, 1, 0, 0);

-- 3.6 SQL 白名单管理菜单
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2330, 'SQL白名单', 2300, '/screen/sql-whitelist', '/views/screen/sql-whitelist/index', NULL, 'Lock', 3, 1, 'screen:sql-whitelist:list', 1, 1, 0, 0);

-- 3.7 SQL 白名单按钮权限
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(2331, '白名单查询', 2330, '', '', NULL, '', 1, 2, 'screen:sql-whitelist:query',  1, 1, 0, 0),
(2332, '白名单新增', 2330, '', '', NULL, '', 2, 2, 'screen:sql-whitelist:add',     1, 1, 0, 0),
(2333, '白名单编辑', 2330, '', '', NULL, '', 3, 2, 'screen:sql-whitelist:edit',    1, 1, 0, 0),
(2334, '白名单删除', 2330, '', '', NULL, '', 4, 2, 'screen:sql-whitelist:remove',  1, 1, 0, 0);

-- 3.8 超级管理员角色授予大屏模块全部菜单/按钮权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 2300),
(1, 2310), (1, 2311), (1, 2312), (1, 2313), (1, 2314), (1, 2315), (1, 2316),
(1, 2320), (1, 2321), (1, 2322), (1, 2323), (1, 2324), (1, 2325),
(1, 2330), (1, 2331), (1, 2332), (1, 2333), (1, 2334);

-- ========================================
-- Part 4: 测试数据源种子数据（演示用，可在生产环境清理）
-- ========================================
-- 注意：HTTP 数据源需在 forge.security.screen.allowed-hosts 中配置放行域名
--       SQL 数据源需确保白名单已包含所查询的表

INSERT INTO `sys_screen_data_source` (`code`, `name`, `type`, `config`, `cache_seconds`, `enabled`, `create_time`, `update_time`, `remark`) VALUES
('ds-http-users', '模拟用户列表（JSONPlaceholder）',
 'HTTP',
 '{"method":"GET","url":"https://jsonplaceholder.typicode.com/users","headers":"{}","params":"{}","timeout":5}',
 60, 1, NOW(), NOW(),
 'JSONPlaceholder 公开测试 API，返回 10 条用户数据，适合演示 HTTP GET 场景'),

('ds-http-posts', '模拟文章列表（JSONPlaceholder）',
 'HTTP',
 '{"method":"GET","url":"https://jsonplaceholder.typicode.com/posts","headers":"{}","params":"{}","timeout":5}',
 60, 1, NOW(), NOW(),
 'JSONPlaceholder 公开测试 API，返回 100 条文章数据'),

('ds-http-chart-demo', '模拟图表数据',
 'HTTP',
 '{"method":"GET","url":"https://api.github.com/repos/vuejs/core/stats/code_frequency","headers":"{}","params":"{}","timeout":10}',
 300, 1, NOW(), NOW(),
 'GitHub 公开 API，返回每周代码变更统计，适合演示图表数据源'),

('ds-sql-sample', '示例 SQL 数据源',
 'SQL',
 '{"sqlTemplate":"SELECT id, name, status, create_time FROM sys_user WHERE deleted = 0","paramSchema":"{}","maxRows":100}',
 120, 1, NOW(), NOW(),
 '查询 forge-admin 系统用户表，用于演示 SQL 类型数据源。需确保 sys_user 在白名单中。');

SET FOREIGN_KEY_CHECKS = 1;

-- ========================================
-- 初始化完成
-- ========================================
-- 后续使用：
--   1. 后端启动时 Flyway 会跳过本脚本（已迁移过），按 V2026xxx 顺序执行增量变更
--   2. 前端访问 /screen/index 即可看到大屏列表菜单
--   3. 首次进入大屏编辑器（apps/forge-screen）需启动该 SPA
--   4. 在「SQL 白名单」中维护可查询的表/列，确保 SQL 数据源安全
-- ========================================

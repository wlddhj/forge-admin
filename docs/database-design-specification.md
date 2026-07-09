# 数据库设计规范

## 1. 基本原则

- 数据库版本：MySQL 8.0+
- 字符集：utf8mb4
- 排序规则：utf8mb4_unicode_ci（推荐）或 utf8mb4_0900_ai_ci
- 存储引擎：InnoDB
- 所有表、字段必须添加注释

## 2. 表命名规范

### 2.1 命名格式

| 模块 | 前缀 | 示例 |
|------|------|------|
| 系统模块 | `sys_` | sys_user, sys_role, sys_menu |
| 工作流模块 | `wf_` | wf_category, wf_form, wf_process_deploy_ext |
| 应用模块 | `app_` | app_user |
| OAuth2 | `oauth2_` | oauth2_registered_client |

### 2.2 命名规则

- 使用小写字母 + 下划线分隔：`snake_case`
- 表名应体现业务含义，避免缩写
- 关联表格式：`{主表}_{关联表}`，如 `sys_role_menu`、`sys_user_role`
- 扩展表格式：`{主表}_ext`，如 `wf_process_deploy_ext`

### 2.3 禁止事项

- 禁止使用 MySQL 关键字作为表名
- 禁止使用数字开头的表名
- 禁止使用空格或特殊字符

## 3. 字段命名规范

### 3.1 命名格式

- 使用小写字母 + 下划线分隔：`snake_case`
- 字段名应具有自描述性
- Boolean 类型字段使用 `is_`、`has_` 前缀，如 `is_system`、`is_default`
- 时间字段统一使用 `_time` 后缀，如 `create_time`、`update_time`

### 3.2 主键

```sql
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID'
```

### 3.3 外键引用

- 用户引用：`user_id`、`create_by`、`update_by`
- 部门引用：`dept_id`
- 角色引用：`role_id`
- 菜单引用：`menu_id`

## 4. 公共字段规范

### 4.1 标准公共字段

所有业务表应包含以下公共字段：

```sql
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`deleted` tinyint DEFAULT 0 COMMENT '删除标记(0:正常 1:已删除)',
```

### 4.2 完整公共字段（推荐）

```sql
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`create_by` bigint DEFAULT NULL COMMENT '创建人',
`update_by` bigint DEFAULT NULL COMMENT '更新人',
`deleted` tinyint DEFAULT 0 COMMENT '删除标记(0:正常 1:已删除)',
`status` tinyint DEFAULT 1 COMMENT '状态(0:禁用 1:启用)',
`remark` varchar(255) DEFAULT NULL COMMENT '备注',
```

### 4.3 公共字段说明

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键，自增 |
| create_time | datetime | CURRENT_TIMESTAMP | 创建时间，自动填充 |
| update_time | datetime | ON UPDATE CURRENT_TIMESTAMP | 更新时间，自动更新 |
| create_by | bigint | NULL | 创建人ID |
| update_by | bigint | NULL | 更新人ID |
| deleted | tinyint | 0 | 软删除标记：0=正常，1=已删除 |
| status | tinyint | 1 | 状态：0=禁用，1=启用 |

## 5. 字段类型规范

### 5.1 数值类型

| 场景 | 类型 | 说明 |
|------|------|------|
| 主键、ID引用 | bigint | 大整数，支持大数据量 |
| 状态、类型、标记 | tinyint | 小整数，范围 -128~127 |
| 排序、数量 | int | 整数 |
| 金额、精确数值 | decimal(18,4) | 定点数，避免精度丢失 |

**禁止使用 float、double 存储金额等精确数值。**

### 5.2 字符串类型

| 场景 | 类型 | 长度建议 |
|------|------|----------|
| 名称、标题 | varchar(50~100) | 根据实际需求 |
| 编码、标识 | varchar(50~100) | 唯一索引 |
| 描述、备注 | varchar(255~500) | 简短描述 |
| 长文本、内容 | text | 不限长度 |
| JSON数据 | text/json | 配置、参数 |
| URL、路径 | varchar(200~500) | 路径地址 |
| MD5、哈希 | varchar(32~64) | 固定长度 |

### 5.3 时间类型

| 场景 | 类型 | 格式 |
|------|------|------|
| 创建/更新时间 | datetime | YYYY-MM-DD HH:MM:SS |
| 业务时间 | datetime | YYYY-MM-DD HH:MM:SS |
| 仅日期 | date | YYYY-MM-DD |
| 仅时间 | time | HH:MM:SS |
| 时间戳（API） | bigint | 毫秒级 Unix 时间戳 |

### 5.4 Boolean 类型

使用 `tinyint` 替代 `boolean`：

```sql
`is_system` tinyint DEFAULT 0 COMMENT '是否系统内置(0:否 1:是)'
`is_default` tinyint DEFAULT 0 COMMENT '是否默认(0:否 1:是)'
```

## 6. 索引设计规范

### 6.1 主键索引

```sql
PRIMARY KEY (`id`)
```

### 6.2 唯一索引

```sql
UNIQUE KEY `uk_username` (`username`)
UNIQUE KEY `uk_category_code` (`category_code`)
```

命名格式：`uk_{字段名}`

### 6.3 普通索引

```sql
KEY `idx_parent_id` (`parent_id`)
KEY `idx_dept_id` (`dept_id`)
KEY `idx_create_time` (`create_time`)
KEY `idx_process_instance_id` (`process_instance_id`)
```

命名格式：`idx_{字段名}`

### 6.4 复合索引

```sql
KEY `idx_biz` (`biz_type`, `biz_id`)
UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
```

命名格式：`idx_{含义}` 或 `uk_{含义}`

### 6.5 索引原则

1. 外键字段必须建索引
2. 高频查询条件字段建索引
3. 复合索引遵循最左前缀原则
4. 避免在低选择性字段建索引（如 status）
5. 避免过度索引，影响写入性能

## 7. 软删除规范

### 7.1 字段定义

```sql
`deleted` tinyint DEFAULT 0 COMMENT '删除标记(0:正常 1:已删除)'
```

### 7.2 查询规范

所有查询必须过滤已删除数据：

```sql
SELECT * FROM sys_user WHERE deleted = 0 AND ...
```

MyBatis Plus 通过 `@TableLogic` 注解自动处理：

```java
@TableLogic
private Integer deleted;
```

## 8. 外键约束

### 8.1 禁止物理外键

项目不使用物理外键约束，原因：
- 影响性能
- 限制数据灵活性
- 不利于分库分表

### 8.2 逻辑外键

通过代码层面保证数据一致性：
- Service 层事务控制
- 关联表数据同步删除/更新

## 9. 迁移脚本规范

### 9.1 脚本位置

```
apps/forge-server/forge-server/src/main/resources/db/migration/
```

### 9.2 命名格式

```
V{YYYYMMDD}{seq}__{description}.sql
```

示例：
```
V2026052501__workflow_tables.sql
V2026052601__wf_form_table.sql
V2026052701__wf_expression_listener.sql
```

### 9.3 脚本内容规范

```sql
-- ========================================
-- 标题说明
-- ========================================

-- 表结构变更使用 CREATE TABLE IF NOT EXISTS 或 ALTER TABLE
CREATE TABLE IF NOT EXISTS `table_name` (
    ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表注释';

-- 菜单、字典等数据插入
INSERT INTO `sys_menu` (...) VALUES (...);

-- 角色授权
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (...);
```

### 9.4 脚本要求

1. 使用 `IF NOT EXISTS` 避免重复创建
2. 必须添加注释说明变更内容
3. 菜单数据需同步角色授权
4. 字典数据需先插入类型再插入数据
5. 每个脚本只做一件事，避免混合多个变更

## 10. 菜单数据规范

### 10.1 菜单表字段

```sql
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
  ...
);
```

### 10.2 菜单类型

| menu_type | 说明 |
|-----------|------|
| 0 | 目录（Layout，含子菜单） |
| 1 | 菜单（具体页面） |
| 2 | 按钮（操作权限） |

### 10.3 菜单插入格式

```sql
INSERT INTO `sys_menu` 
(`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) 
VALUES
(100, '流程管理', 0, '/workflow', 'Layout', '/workflow/process', 'SetUp', 2, 0, NULL, 1, 1, 0, 0);
```

### 10.4 权限标识格式

```
{模块}:{实体}:{操作}
```

示例：
- `system:user:list` - 用户列表
- `system:user:add` - 用户新增
- `workflow:process:deploy` - 流程部署

## 11. 字典数据规范

### 11.1 字典类型表

```sql
INSERT INTO `sys_dict_type` 
(`id`, `dict_name`, `dict_type`, `status`, `is_system`, `remark`) 
VALUES
(1, '用户性别', 'sys_user_sex', 1, 1, '用户性别字典');
```

### 11.2 字典数据表

```sql
INSERT INTO `sys_dict_data` 
(`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `status`, `list_class`) 
VALUES
(1, 'sys_user_sex', '男', '0', 1, 'primary', 1);
```

### 11.3 字典命名规范

| 模块 | 前缀 | 示例 |
|------|------|------|
| 系统字典 | `sys_` | sys_user_sex, sys_normal_disable |
| 工作流字典 | `wf_` | wf_action_type |

### 11.4 list_class 样式

| 值 | Element Plus 标签样式 |
|----|----------------------|
| primary | 蓝色 |
| success | 绿色 |
| warning | 黄色 |
| danger | 红色 |
| info | 灰色 |

## 12. SQL 脚本模板

### 12.1 初始化脚本模板

```sql
-- ========================================
-- {项目名} {模块名} 初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建时间: YYYY-MM-DD
-- 说明: 本脚本包含 {模块名} 相关的表结构和数据
-- 前置依赖: sql/init.sql (如有)
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `forge_admin`;

-- ========================================
-- 1. {表名}表
-- ========================================
DROP TABLE IF EXISTS `table_name`;
CREATE TABLE `table_name` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  -- 业务字段
  `field_name` varchar(100) NOT NULL COMMENT '字段说明',
  -- 公共字段
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_field` (`field_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表注释';

-- ========================================
-- 菜单配置
-- ========================================
INSERT INTO `sys_menu` (...) VALUES (...);

-- ========================================
-- 角色授权
-- ========================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (...);

-- ========================================
-- 字典数据
-- ========================================
INSERT INTO `sys_dict_type` (...) VALUES (...);
INSERT INTO `sys_dict_data` (...) VALUES (...);

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT '{模块名}初始化完成!' AS message;
```

### 12.2 迁移脚本模板

```sql
-- ========================================
-- {变更说明}
-- ========================================

-- 表结构变更
CREATE TABLE IF NOT EXISTS `new_table` (...);

-- 或 ALTER TABLE
ALTER TABLE `existing_table` 
  ADD COLUMN `new_field` varchar(100) DEFAULT NULL COMMENT '字段说明' AFTER `existing_field`;

-- 菜单配置
INSERT INTO `sys_menu` (...) VALUES (...);

-- 角色授权
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (...);
```

## 13. 最佳实践

### 13.1 性能优化

1. 合理使用索引，避免全表扫描
2. 大表考虑分区策略
3. 避免 SELECT *，只查询需要的字段
4. 批量操作使用批量插入/更新

### 13.2 数据安全

1. 密码字段使用 varchar(100) 存储 BCrypt 哈希
2. 敏感字段（token、secret）考虑加密存储
3. 使用软删除替代物理删除
4. 重要数据变更记录操作日志

### 13.3 兼容性

1. 避免使用 MySQL 8.0 以下版本的特性
2. 字符集统一 utf8mb4，支持 emoji
3. 时间字段使用 datetime，避免 timestamp 的时区问题
4. 命名避免使用各数据库的保留字

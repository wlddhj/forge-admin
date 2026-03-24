-- ============================================
-- 数据权限功能测试数据
-- ============================================
-- 用于验证数据权限功能的完整测试场景
-- ============================================

-- 注意：此脚本仅用于测试环境
-- 执行前请确保已创建相关表结构

-- ============================================
-- 1. 测试部门数据
-- ============================================

-- 清理现有测试数据
DELETE FROM sys_user WHERE username IN ('admin_test', 'manager_101', 'member_101', 'member_103', 'self_only', 'custom_user');
DELETE FROM sys_role WHERE role_code IN ('admin_test', 'dept_manager', 'dept_member', 'self_only', 'custom_dept');
DELETE FROM sys_dept WHERE id BETWEEN 100 AND 199;
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE '%_test');
DELETE FROM sys_role_dept WHERE role_id IN (SELECT id FROM sys_role WHERE role_code LIKE '%_test');

-- 创建测试部门层级结构
-- 结构：
--   100: 总公司
--     101: 研发部
--       103: 研发一组
--       104: 研发二组
--     102: 销售部

INSERT INTO sys_dept (id, dept_name, parent_id, ancestors, sort_order, leader, phone, email, status, deleted, create_time, update_time) VALUES
(100, '总公司', 0, '0', 0, NULL, NULL, NULL, 1, 0, NOW(), NOW()),
(101, '研发部', 100, '0,100', 1, NULL, NULL, NULL, 1, 0, NOW(), NOW()),
(102, '销售部', 100, '0,100', 2, NULL, NULL, NULL, 1, 0, NOW(), NOW()),
(103, '研发一组', 101, '0,100,101', 1, NULL, NULL, NULL, 1, 0, NOW(), NOW()),
(104, '研发二组', 101, '0,100,101', 2, NULL, NULL, NULL, 1, 0, NOW(), NOW());

-- ============================================
-- 2. 测试角色数据
-- ============================================

-- 角色说明：
-- data_scope: 1=全部, 2=自定义, 3=本部门, 4=本部门及以下, 5=仅本人

INSERT INTO sys_role (id, role_code, role_name, data_scope, status, deleted, sort_order, remark, create_time, update_time) VALUES
(10, 'admin_test', '测试超级管理员', '1', 1, 0, 1, '拥有全部数据权限', NOW(), NOW()),
(11, 'dept_manager', '部门经理', '3', 1, 0, 2, '只能查看本部门数据', NOW(), NOW()),
(12, 'dept_member', '部门成员', '4', 1, 0, 3, '可查看本部门及子部门数据', NOW(), NOW()),
(13, 'self_only', '仅本人权限', '5', 1, 0, 4, '只能查看自己的数据', NOW(), NOW()),
(14, 'custom_dept', '自定义部门权限', '2', 1, 0, 5, '可查看指定部门数据', NOW(), NOW());

-- ============================================
-- 3. 自定义角色-部门关联数据
-- ============================================

-- 自定义部门权限角色可以访问研发部(101)和销售部(102)
INSERT INTO sys_role_dept (role_id, dept_id) VALUES
(14, 101),  -- 可访问研发部
(14, 102);  -- 可访问销售部

-- ============================================
-- 4. 测试用户数据
-- ============================================

-- 密码都是 123456 (BCrypt 加密后的值，实际使用时需要替换)
-- $2a$10$... 是 BCrypt 格式的占位符

INSERT INTO sys_user (id, username, nickname, dept_id, account_type, password, status, deleted, create_time, update_time) VALUES
-- 超级管理员 (account_type=1, 拥有全部数据权限)
(100, 'admin_test', '测试管理员', 100, 1, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 0, NOW(), NOW()),

-- 部门经理 (data_scope=3, 本部门数据权限)
(101, 'manager_101', '研发部经理', 101, 0, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 0, NOW(), NOW()),

-- 部门成员 (data_scope=4, 本部门及以下数据权限)
(102, 'member_101', '研发部成员', 101, 0, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 0, NOW(), NOW()),

-- 研发一组员工 (data_scope=3, 本部门数据权限)
(103, 'member_103', '研发一组员工', 103, 0, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 0, NOW(), NOW()),

-- 仅本人权限用户 (data_scope=5, 仅本人数据权限)
(104, 'self_only', '仅本人权限', 100, 0, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 0, NOW(), NOW()),

-- 自定义部门权限用户 (data_scope=2, 自定义数据权限)
(105, 'custom_user', '自定义权限用户', 100, 0, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 0, NOW(), NOW());

-- ============================================
-- 5. 用户-角色关联数据
-- ============================================

INSERT INTO sys_user_role (user_id, role_id) VALUES
-- admin_test -> 超级管理员角色
(100, 10),

-- manager_101 -> 部门经理角色
(101, 11),

-- member_101 -> 部门成员角色
(102, 12),

-- member_103 -> 部门成员角色 (本部门数据权限)
(103, 11),

-- self_only -> 仅本人权限角色
(104, 13),

-- custom_user -> 自定义部门权限角色
(105, 14);

-- ============================================
-- 6. 验证数据完整性
-- ============================================

-- 查询部门结构
-- SELECT id, dept_name, parent_id, ancestors FROM sys_dept WHERE id BETWEEN 100 AND 199 ORDER BY id;

-- 查询角色配置
-- SELECT id, role_code, role_name, data_scope FROM sys_role WHERE id BETWEEN 10 AND 14 ORDER BY id;

-- 查询用户配置
-- SELECT id, username, nickname, dept_id, account_type FROM sys_user WHERE id BETWEEN 100 AND 105 ORDER BY id;

-- 查询用户角色关联
-- SELECT u.id, u.username, r.role_code, r.data_scope
-- FROM sys_user u
-- LEFT JOIN sys_user_role ur ON u.id = ur.user_id
-- LEFT JOIN sys_role r ON ur.role_id = r.id
-- WHERE u.id BETWEEN 100 AND 105
-- ORDER BY u.id;

-- ============================================
-- 7. 测试场景说明
-- ============================================

/*
测试场景和预期结果：

1. admin_test (id=100)
   - 类型：超级管理员 (account_type=1)
   - 预期：可以看到所有用户（无过滤）

2. manager_101 (id=101)
   - 角色：部门经理 (data_scope=3, 本部门)
   - 部门：101 (研发部)
   - 预期：只能看到 dept_id=101 的用户

3. member_101 (id=102)
   - 角色：部门成员 (data_scope=4, 本部门及以下)
   - 部门：101 (研发部)
   - 预期：可以看到 dept_id IN (101, 103, 104) 的用户

4. member_103 (id=103)
   - 角色：部门成员 (data_scope=3, 本部门)
   - 部门：103 (研发一组)
   - 预期：只能看到 dept_id=103 的用户

5. self_only (id=104)
   - 角色：仅本人权限 (data_scope=5)
   - 预期：只能看到 id=104 的用户（自己）

6. custom_user (id=105)
   - 角色：自定义部门权限 (data_scope=2)
   - 部门：100 (总公司)
   - 关联部门：101, 102
   - 预期：可以看到 dept_id IN (101, 102) 的用户
*/

-- ============================================
-- 8. 测试后清理数据（可选）
-- ============================================

-- 取消注释以下内容来清理测试数据
/*
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE '%_test');
DELETE FROM sys_role_dept WHERE role_id BETWEEN 10 AND 14;
DELETE FROM sys_user WHERE id BETWEEN 100 AND 105;
DELETE FROM sys_role WHERE id BETWEEN 10 AND 14;
DELETE FROM sys_dept WHERE id BETWEEN 100 AND 104;
*/

-- ============================================
-- 结束
-- ============================================

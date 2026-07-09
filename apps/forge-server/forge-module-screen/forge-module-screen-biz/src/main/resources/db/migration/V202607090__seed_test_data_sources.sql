-- ========================================
-- 大屏数据源测试/演示用种子数据
-- ========================================
-- 包含 HTTP 和 SQL 类型数据源，便于功能验证。

INSERT INTO sys_screen_data_source (code, name, type, config, cache_seconds, enabled, remark) VALUES
('ds-http-users', '模拟用户列表（JSONPlaceholder）',
 'HTTP',
 '{"method":"GET","url":"https://jsonplaceholder.typicode.com/users","headers":"{}","params":"{}","timeout":5}',
 60, 1,
 'JSONPlaceholder 公开测试 API，返回 10 条用户数据，适合演示 HTTP GET 场景'),

('ds-http-posts', '模拟文章列表（JSONPlaceholder）',
 'HTTP',
 '{"method":"GET","url":"https://jsonplaceholder.typicode.com/posts","headers":"{}","params":"{}","timeout":5}',
 60, 1,
 'JSONPlaceholder 公开测试 API，返回 100 条文章数据'),

('ds-http-chart-demo', '模拟图表数据',
 'HTTP',
 '{"method":"GET","url":"https://api.github.com/repos/vuejs/core/stats/code_frequency","headers":"{}","params":"{}","timeout":10}',
 300, 1,
 'GitHub 公开 API，返回每周代码变更统计，适合演示图表数据源')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO sys_screen_data_source (code, name, type, config, cache_seconds, enabled, remark) VALUES
('ds-sql-sample', '示例 SQL 数据源',
 'SQL',
 '{"sqlTemplate":"SELECT id, name, status, create_time FROM sys_user WHERE deleted = 0","paramSchema":"{}","maxRows":100}',
 120, 1,
 '查询 forge-admin 系统用户表，用于演示 SQL 类型数据源。确保数据库连接可用。')
ON DUPLICATE KEY UPDATE name = VALUES(name);

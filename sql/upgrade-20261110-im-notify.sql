-- ============================================================
-- 升级脚本：审批消息推送企业微信/钉钉（IM 工作通知）
-- 日期：2026-11-10
-- 说明：仅 DML，无表结构变更。dev 库无 Flyway，需手工执行本脚本。
--   sys_config 键缺失时代码有默认值兜底（enabled=true, channels=wework,dingtalk），
--   本脚本仅将开关显式化，便于管理员在系统配置页发现与调整。
-- 凭据通过环境变量注入（见 application.yml im.notify 段注释），不入库。
-- ============================================================

-- IM 推送运行时开关（sys_config uk_config_key 唯一，NOT EXISTS 保证幂等）
INSERT INTO sys_config (config_name, config_key, config_value, config_type, config_group, is_system, status, remark)
SELECT 'IM推送总开关', 'im.notify.enabled', 'true', 'boolean', 'workflow', 0, 1, '审批消息推送企微/钉钉总开关（凭据另需 yml 配置 im.notify.*）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config x WHERE x.config_key = 'im.notify.enabled');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, config_group, is_system, status, remark)
SELECT 'IM推送渠道', 'im.notify.channels', 'wework,dingtalk', 'text', 'workflow', 0, 1, '启用的渠道白名单，逗号分隔：wework,dingtalk'
WHERE NOT EXISTS (SELECT 1 FROM sys_config x WHERE x.config_key = 'im.notify.channels');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, config_group, is_system, status, remark)
SELECT 'IM消息跳转域名', 'im.notify.web-base-url', 'http://localhost:3000', 'text', 'workflow', 0, 1, 'IM 消息点击跳转的前端地址（生产改为实际域名）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config x WHERE x.config_key = 'im.notify.web-base-url');

-- 测试发送按钮权限（挂在业务回调菜单 260 下；id 265 沿既有号段顺延）
INSERT INTO sys_menu (id, menu_name, parent_id, route_path, component_path, redirect_path, icon, sort_order, menu_type, permission, status, visible, is_external, is_cached)
SELECT 265, 'IM通知测试', 260, '', '', NULL, '', 5, 2, 'workflow:im:test', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'workflow:im:test');

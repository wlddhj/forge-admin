-- ========================================
-- 系统默认主题配置种子数据
-- 创建时间：2026-09-23
-- 说明：新用户/无本地主题配置用户首次进入时的初始主题
--   四个 key 均 config_group=brand，未配置时后端逐字段回退默认值
--   mode=auto 表示跟随浏览器 prefers-color-scheme（与存量行为一致）
-- ========================================

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`)
SELECT '默认主题-调色板', 'sys.brand.theme.palette', 'blue', 'text', 'brand', 1, 1, '系统默认主题调色板：blue/purple/green/crimson/orange/cyan/teal'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.brand.theme.palette' AND `deleted` = 0);

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`)
SELECT '默认主题-布局', 'sys.brand.theme.layout', 'sidebar', 'text', 'brand', 1, 1, '系统默认主题布局：sidebar/top'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.brand.theme.layout' AND `deleted` = 0);

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`)
SELECT '默认主题-风格', 'sys.brand.theme.style', 'flat', 'text', 'brand', 1, 1, '系统默认主题风格：flat/glass/card/compact'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.brand.theme.style' AND `deleted` = 0);

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`)
SELECT '默认主题-明暗模式', 'sys.brand.theme.mode', 'auto', 'text', 'brand', 1, 1, '系统默认明暗模式：light/dark/auto（auto 跟随浏览器）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.brand.theme.mode' AND `deleted` = 0);

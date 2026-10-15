-- ========================================
-- 模型故障转移开关
-- 创建时间：2026-10-15
-- 说明：非流式调用主模型失败时自动切换备用模型（同类型/启用/最多3个）。
--       读取容错：解析失败视为开启（可用性优先）。
-- 幂等性：INSERT IGNORE（config_key 唯一）
-- ========================================

INSERT IGNORE INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`) VALUES
('AI模型故障转移开关', 'ai.model.fallback-enabled', '1', 'boolean', 'ai', 1, 1, '非流式调用主模型失败时自动切换同类型备用模型（0=关闭）');

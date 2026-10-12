-- ========================================
-- AI 模型配置增强：模型类型字段 + ai_model_type 字典 + 模型新增/删除按钮权限
-- 创建时间：2026-10-12
-- 说明：
--   1. ai_model_config 增加 model_type 列（存量数据自动归为 text）
--   2. 新增字典 ai_model_type（8 种模型类型），后端逻辑约定值 text 与字典 value 保持一致
--   3. 补齐 ai:model:add / ai:model:delete 按钮权限并授权给原拥有模型配置按钮的角色
-- 幂等性：菜单/授权/字典段可重复执行；加列段重复执行会因列已存在报错（手工按版本执行一次即可）
-- ========================================

-- 1. 模型类型列
ALTER TABLE `ai_model_config`
  ADD COLUMN `model_type` varchar(20) NOT NULL DEFAULT 'text' COMMENT '模型类型(text/multimodal/image/video/ocr/asr/tts/embedding)' AFTER `provider`,
  ADD KEY `idx_model_type` (`model_type`);

-- 2. 模型类型字典
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'ai_model_type';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'ai_model_type';
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `is_system`, `remark`) VALUES
('AI模型类型', 'ai_model_type', 1, 1, 'AI模型能力类型');
INSERT INTO `sys_dict_data` (`dict_type`, `dict_label`, `dict_value`, `dict_sort`, `list_class`, `status`) VALUES
('ai_model_type', '文本生成',   'text',       1, 'primary', 1),
('ai_model_type', '多模态理解', 'multimodal', 2, 'success', 1),
('ai_model_type', '图像生成',   'image',      3, 'warning', 1),
('ai_model_type', '视频生成',   'video',      4, 'danger',  1),
('ai_model_type', 'OCR',        'ocr',        5, 'info',    1),
('ai_model_type', '语音识别',   'asr',        6, 'primary', 1),
('ai_model_type', '语音合成',   'tts',        7, 'success', 1),
('ai_model_type', '向量检索',   'embedding',  8, 'info',    1);

-- 3. 模型新增/删除按钮权限（id 324/325 已确认空闲）
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (324, 325);
DELETE FROM `sys_menu` WHERE `id` IN (324, 325);
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `route_path`, `component_path`, `redirect_path`, `icon`, `sort_order`, `menu_type`, `permission`, `status`, `visible`, `is_external`, `is_cached`) VALUES
(324, '模型新增', 320, '', '', NULL, '', 4, 2, 'ai:model:add',    1, 1, 0, 0),
(325, '模型删除', 320, '', '', NULL, '', 5, 2, 'ai:model:delete', 1, 1, 0, 0);

-- 4. 授权给所有已拥有模型配置按钮（321/322/323）的角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
CROSS JOIN (SELECT 324 AS `id` UNION SELECT 325) m
WHERE rm.`menu_id` IN (321, 322, 323);

-- ========================================
-- 模型配置：思考模式开关
-- 创建时间：2026-10-14
-- 说明：思考类模型（GLM-4.x 等）输出先写 reasoning_content 再写 content，
--       思考 token 计入 max_tokens，长 prompt 下预算被思考耗尽导致 content 恒空。
--       thinking_disabled=1 时构建的模型实例在请求 body 注入 thinking.type=disabled 关闭思考。
-- 幂等性：ALTER 非幂等，重复执行报 Duplicate column 可忽略
-- ========================================

ALTER TABLE `ai_model_config` ADD COLUMN `thinking_disabled` TINYINT NOT NULL DEFAULT 0 COMMENT '禁用思考模式(0:默认 1:请求注入thinking.type=disabled)' AFTER `temperature`;

-- 默认模型（GLM-4.7-FlashX）默认关闭思考：Agent 决策等内部节点只需结构化 JSON，思考徒耗预算
UPDATE `ai_model_config` SET `thinking_disabled` = 1 WHERE `id` = 5;

-- ========================================
-- AI 模型端点迁移：Java（Spring AI）原生实现替代 Python 服务
-- 创建时间：2026-09-28
-- 说明：统一为 OpenAI 兼容协议 base-url（不含 /chat/completions 后缀）
--   deepseek/glm 原值含后缀；qwen 原值为 DashScope 原生 API，必须迁移
--   ernie 新增千帆 v2 兼容配置（api_key 置空，需运维在模型管理中补配）
-- ========================================

UPDATE `ai_model_config`
SET `api_endpoint` = 'https://api.deepseek.com/v1'
WHERE `provider` = 'deepseek' AND `deleted` = 0;

UPDATE `ai_model_config`
SET `api_endpoint` = 'https://open.bigmodel.cn/api/paas/v4'
WHERE `provider` = 'glm' AND `deleted` = 0;

UPDATE `ai_model_config`
SET `api_endpoint` = 'https://dashscope.aliyuncs.com/compatible-mode/v1'
WHERE `provider` = 'qwen' AND `deleted` = 0;

INSERT INTO `ai_model_config` (`id`, `model_name`, `model_code`, `provider`, `api_endpoint`, `api_key`, `max_tokens`, `temperature`, `context_window`, `input_price`, `output_price`, `is_default`, `status`, `remark`)
SELECT 6, '文心一言 ERNIE-4.0', 'ernie-4.0-8k', 'ernie', 'https://qianfan.baidubce.com/v2', '', 4096, 0.7, 8000, 0.012, 0.012, 0, 2, '百度千帆 v2（OpenAI 兼容），需配置 API Key 后启用'
WHERE NOT EXISTS (SELECT 1 FROM `ai_model_config` WHERE `id` = 6 AND `deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `ai_model_config` WHERE `provider` = 'ernie' AND `deleted` = 0);

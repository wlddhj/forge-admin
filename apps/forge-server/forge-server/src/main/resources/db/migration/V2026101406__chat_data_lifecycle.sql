-- ========================================
-- 对话数据生命周期管理
-- 创建时间：2026-10-14
-- 说明：
--   1. 对话保留期参数 2 条（sys_config，group=ai，默认 90 天——用户数据比运行记录保留更长）
--   2. 修正任务一种子表名：ai_conversation → ai_chat_conversation（实际表名带 chat_ 前缀）
-- 幂等性：INSERT IGNORE（config_key 唯一）；UPDATE 幂等
-- ========================================

INSERT IGNORE INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `config_group`, `is_system`, `status`, `remark`) VALUES
('AI对话会话保留天数', 'ai.chat.retention.conversation-days', '90', 'number', 'ai', 1, 1, '对话会话保留天数，超期物理删除（含消息由消息保留期独立清理兜底）'),
('AI对话消息保留天数', 'ai.chat.retention.message-days', '90', 'number', 'ai', 1, 1, '对话消息(content 大字段)保留天数，超期及孤儿消息物理删除');

UPDATE `ai_sql_whitelist` SET `table_name` = 'ai_chat_conversation', `table_comment` = 'AI会话表'
WHERE `table_name` = 'ai_conversation';

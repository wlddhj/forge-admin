-- ============================================================
-- 升级脚本：AI 对话消息表增加思考内容列（ai-thinking-stream）
-- 日期：2026-11-11
-- 说明：思考模型（GLM 等）的 reasoning_content 流式透传后随消息落库，
--       历史会话回看可见思考过程。dev 库无 Flyway，须手工执行。
-- 幂等性：重复执行会报 Duplicate column，属预期（先查 information_schema 亦可）
-- ============================================================

ALTER TABLE `ai_chat_message`
  ADD COLUMN `reasoning_content` text NULL COMMENT '思考内容(思考模型reasoning_content，仅assistant消息)' AFTER `content`;

-- ========================================
-- 流程分类编码层级化迁移
-- V2026102702__wf_category_code_hierarchy.sql
-- ========================================

-- 背景：分类编码改为自动生成的层级码（每级 2 位、字符集 0-9A-Z，子码=父码+2位随机段），
-- 支持按编码前缀模糊查询分类子树。本脚本把存量手填编码迁移为层级码。
-- 业务表（wf_process_ext 等）按 category_id 关联分类，编码变更不影响关联。
--
-- 注意：本脚本按 dev 库 2026-10-27 现存 6 条分类定制（id 对应见下）。
-- 其它环境数据不同时，请按同规则改写：顶级 = 2 位；子级 = 父编码 + 2 位；全局唯一。
-- 幂等：重复执行结果相同。

-- 顶级分类（parent_id = 0）
UPDATE wf_category SET category_code = 'K7' WHERE id = 1;  -- 行政人事
UPDATE wf_category SET category_code = 'Q4' WHERE id = 2;  -- 财务审批
UPDATE wf_category SET category_code = 'Z9' WHERE id = 3;  -- 演示流程

-- 二级分类（编码 = 父编码 + 2 位）
UPDATE wf_category SET category_code = 'K7R5' WHERE id = 4;  -- 请假（父：行政人事）
UPDATE wf_category SET category_code = 'K7X8' WHERE id = 5;  -- 出差（父：行政人事）
UPDATE wf_category SET category_code = 'Q4C2' WHERE id = 6;  -- 报销（父：财务审批）

-- 验证：编码应满足 层级前缀关系 且 全局唯一
-- SELECT id, category_name, category_code, parent_id FROM wf_category WHERE deleted = 0 ORDER BY category_code;

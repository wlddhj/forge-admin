-- ============================================================
-- 升级SQL：AI 智能路由决策审计表
-- 日期：2026-10-26
-- 说明：条件/包容/路由网关启用 AI 路由（节点 callAi 标记）后，
--       每次路由决策落一条审计记录（候选、选中分支、置信度、理由、原始响应）
-- 注意：dev 库未启用 Flyway，请在 forge_admin 库手工执行本脚本
-- ============================================================

USE forge_admin;

DROP TABLE IF EXISTS `wf_ai_route_record`;
CREATE TABLE `wf_ai_route_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `gateway_node_key` VARCHAR(100) NOT NULL COMMENT '网关节点Key',
    `gateway_node_name` VARCHAR(200) DEFAULT NULL COMMENT '网关节点名称',
    `gateway_type` INT DEFAULT NULL COMMENT '网关类型（4条件/9包容/23路由）',
    `candidate_branches` TEXT DEFAULT NULL COMMENT '候选分支JSON [{nodeKey,nodeName}]',
    `selected_branches` VARCHAR(500) DEFAULT NULL COMMENT '选中分支nodeKey，逗号分隔',
    `confidence` INT DEFAULT NULL COMMENT '置信度（0-100）',
    `reasoning` TEXT DEFAULT NULL COMMENT 'AI决策理由',
    `raw_response` TEXT DEFAULT NULL COMMENT '原始AI响应',
    `status` VARCHAR(20) NOT NULL COMMENT '执行状态（SUCCESS/FALLBACK/LOW_CONFIDENCE/TIMEOUT/ERROR）',
    `provider` VARCHAR(50) DEFAULT NULL COMMENT 'AI模型提供商',
    `model_name` VARCHAR(100) DEFAULT NULL COMMENT 'AI模型名称',
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI路由决策记录表';

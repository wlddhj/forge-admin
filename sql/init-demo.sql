-- ========================================
-- 业务示例插件 forge-module-demo（项目立项接入工作流示例，已插件化）
-- 全新安装用；存量库请依次执行 upgrade-20261029-project-example.sql 与 upgrade-20261113-plugin-module.sql
-- 对接文档：docs-base/modules/workflow/06.business-integration-guide.md
-- ========================================

-- 菜单不再手工初始化：启动时插件注册 Runner 按 DemoPluginDescriptor 幂等导入
-- sys_menu（plugin_code='demo' 标记归属），并自动授予内置超管角色；
-- 租户可见性 = 租户套餐勾选 ∩ 该租户插件已启用（License 闸）。

-- 项目立项业务表
CREATE TABLE `demo_biz_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_name` VARCHAR(128) NOT NULL COMMENT '项目名称',
    `total_amount` DECIMAL(14,2) DEFAULT NULL COMMENT '项目金额',
    `biz_user` VARCHAR(64) DEFAULT NULL COMMENT '责任人',
    `dept_name` VARCHAR(64) DEFAULT NULL COMMENT '责任部门',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `process_instance_id` BIGINT DEFAULT NULL COMMENT '流程实例ID（businessKey=project:{id} 发起后回写）',
    `process_no` VARCHAR(64) DEFAULT NULL COMMENT '流程编号（发起回写）',
    `approve_status` VARCHAR(20) NOT NULL DEFAULT 'APPROVING' COMMENT '审批状态 APPROVING/APPROVED/REJECTED/CANCELED（仅由工作流事件回写）',
    `approve_time` DATETIME DEFAULT NULL COMMENT '审批完成时间（终态事件回写）',
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（多租户拦截器自动隔离）',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人（发起人）',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`approve_status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目立项表（业务示例插件）';

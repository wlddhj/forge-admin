-- ========================================
-- 创建 FlowLong 核心表
-- V2026062501__create_flowlong_tables.sql
-- 基于 FlowLong 官方 MySQL 脚本
-- ========================================

-- 流程定义表
CREATE TABLE IF NOT EXISTS flw_process (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    create_id VARCHAR(50) NOT NULL COMMENT '创建人ID',
    create_by VARCHAR(50) NOT NULL COMMENT '创建人名称',
    create_time TIMESTAMP NOT NULL COMMENT '创建时间',
    process_key VARCHAR(100) NOT NULL COMMENT '流程定义 key 唯一标识',
    process_name VARCHAR(100) NOT NULL COMMENT '流程定义名称',
    process_icon VARCHAR(255) DEFAULT NULL COMMENT '流程图标地址',
    process_type VARCHAR(100) COMMENT '流程类型',
    process_version INT NOT NULL DEFAULT 1 COMMENT '流程版本，默认 1',
    instance_url VARCHAR(200) COMMENT '实例地址',
    remark VARCHAR(255) COMMENT '备注说明',
    use_scope TINYINT NOT NULL DEFAULT 0 COMMENT '使用范围 0，全员 1，指定人员（业务关联） 2，均不可提交',
    process_state TINYINT NOT NULL DEFAULT 1 COMMENT '流程状态 0，不可用 1，可用 2，历史版本',
    model_content LONGTEXT COMMENT '流程模型定义JSON内容',
    sort TINYINT DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    INDEX idx_process_name (process_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义表';

-- 流程实例表（活动）
CREATE TABLE IF NOT EXISTS flw_instance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    create_id VARCHAR(50) NOT NULL COMMENT '创建人ID',
    create_by VARCHAR(50) NOT NULL COMMENT '创建人名称',
    create_time TIMESTAMP NOT NULL COMMENT '创建时间',
    process_id BIGINT NOT NULL COMMENT '流程定义ID',
    parent_instance_id BIGINT COMMENT '父流程实例ID',
    priority TINYINT COMMENT '优先级',
    instance_no VARCHAR(50) COMMENT '流程实例编号',
    business_key VARCHAR(100) COMMENT '业务KEY',
    variable LONGTEXT COMMENT '变量json',
    current_node_name VARCHAR(100) NOT NULL COMMENT '当前所在节点名称',
    current_node_key VARCHAR(100) NOT NULL COMMENT '当前所在节点key',
    expire_time TIMESTAMP NULL DEFAULT NULL COMMENT '期望完成时间',
    last_update_by VARCHAR(50) COMMENT '上次更新人',
    last_update_time TIMESTAMP NULL DEFAULT NULL COMMENT '上次更新时间',
    PRIMARY KEY (id),
    INDEX idx_instance_process_id (process_id),
    CONSTRAINT fk_instance_process_id FOREIGN KEY (process_id) REFERENCES flw_process (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例表';

-- 历史流程实例表
CREATE TABLE IF NOT EXISTS flw_his_instance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    create_id VARCHAR(50) NOT NULL COMMENT '创建人ID',
    create_by VARCHAR(50) NOT NULL COMMENT '创建人名称',
    create_time TIMESTAMP NOT NULL COMMENT '创建时间',
    process_id BIGINT NOT NULL COMMENT '流程定义ID',
    parent_instance_id BIGINT COMMENT '父流程实例ID',
    priority TINYINT COMMENT '优先级',
    instance_no VARCHAR(50) COMMENT '流程实例编号',
    business_key VARCHAR(100) COMMENT '业务KEY',
    variable LONGTEXT COMMENT '变量json',
    current_node_name VARCHAR(100) NOT NULL COMMENT '当前所在节点名称',
    current_node_key VARCHAR(100) NOT NULL COMMENT '当前所在节点key',
    expire_time TIMESTAMP NULL DEFAULT NULL COMMENT '期望完成时间',
    last_update_by VARCHAR(50) COMMENT '上次更新人',
    last_update_time TIMESTAMP NULL DEFAULT NULL COMMENT '上次更新时间',
    instance_state TINYINT NOT NULL DEFAULT 0 COMMENT '状态 -2，已暂停状态 -1，暂存待审 0，审批中 1，审批通过 2，审批拒绝 3，撤销审批 4，超时结束 5，强制终止 6，自动通过 7，自动拒绝',
    end_time TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间',
    duration BIGINT COMMENT '处理耗时',
    PRIMARY KEY (id),
    INDEX idx_his_instance_process_id (process_id),
    CONSTRAINT fk_his_instance_process_id FOREIGN KEY (process_id) REFERENCES flw_process (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史流程实例表';

-- 任务表（活动）
CREATE TABLE IF NOT EXISTS flw_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    create_id VARCHAR(50) NOT NULL COMMENT '创建人ID',
    create_by VARCHAR(50) NOT NULL COMMENT '创建人名称',
    create_time TIMESTAMP NOT NULL COMMENT '创建时间',
    instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    parent_task_id BIGINT COMMENT '父任务ID',
    call_process_id BIGINT COMMENT '调用外部流程定义ID',
    call_instance_id BIGINT COMMENT '调用外部流程实例ID',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_key VARCHAR(100) NOT NULL COMMENT '任务 key 唯一标识',
    task_type TINYINT NOT NULL COMMENT '任务类型',
    perform_type TINYINT COMMENT '参与类型',
    action_url VARCHAR(200) COMMENT '任务处理的url',
    variable LONGTEXT COMMENT '变量json',
    assignor_id VARCHAR(100) COMMENT '委托人ID',
    assignor VARCHAR(255) COMMENT '委托人',
    expire_time TIMESTAMP NULL DEFAULT NULL COMMENT '任务期望完成时间',
    remind_time TIMESTAMP NULL DEFAULT NULL COMMENT '提醒时间',
    remind_repeat TINYINT NOT NULL DEFAULT 0 COMMENT '提醒次数',
    viewed TINYINT NOT NULL DEFAULT 0 COMMENT '已阅 0，否 1，是',
    PRIMARY KEY (id),
    INDEX idx_task_instance_id (instance_id),
    CONSTRAINT fk_task_instance_id FOREIGN KEY (instance_id) REFERENCES flw_instance (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- 历史任务表
CREATE TABLE IF NOT EXISTS flw_his_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    create_id VARCHAR(50) NOT NULL COMMENT '创建人ID',
    create_by VARCHAR(50) NOT NULL COMMENT '创建人名称',
    create_time TIMESTAMP NOT NULL COMMENT '创建时间',
    instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    parent_task_id BIGINT COMMENT '父任务ID',
    call_process_id BIGINT COMMENT '调用外部流程定义ID',
    call_instance_id BIGINT COMMENT '调用外部流程实例ID',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_key VARCHAR(100) NOT NULL COMMENT '任务 key 唯一标识',
    task_type TINYINT NOT NULL COMMENT '任务类型',
    perform_type TINYINT COMMENT '参与类型',
    action_url VARCHAR(200) COMMENT '任务处理的url',
    variable LONGTEXT COMMENT '变量json',
    assignor_id VARCHAR(100) COMMENT '委托人ID',
    assignor VARCHAR(255) COMMENT '委托人',
    expire_time TIMESTAMP NULL DEFAULT NULL COMMENT '任务期望完成时间',
    remind_time TIMESTAMP NULL DEFAULT NULL COMMENT '提醒时间',
    remind_repeat TINYINT NOT NULL DEFAULT 0 COMMENT '提醒次数',
    viewed TINYINT NOT NULL DEFAULT 0 COMMENT '已阅 0，否 1，是',
    finish_time TIMESTAMP NULL DEFAULT NULL COMMENT '任务完成时间',
    task_state TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态',
    duration BIGINT COMMENT '处理耗时',
    PRIMARY KEY (id),
    INDEX idx_his_task_instance_id (instance_id),
    INDEX idx_his_task_parent_task_id (parent_task_id),
    CONSTRAINT fk_his_task_instance_id FOREIGN KEY (instance_id) REFERENCES flw_his_instance (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史任务表';

-- 任务参与者表（活动）
CREATE TABLE IF NOT EXISTS flw_task_actor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    actor_id VARCHAR(100) NOT NULL COMMENT '参与者ID',
    actor_name VARCHAR(100) NOT NULL COMMENT '参与者名称',
    actor_type INT NOT NULL COMMENT '参与者类型 0，用户 1，角色 2，部门',
    weight INT COMMENT '权重',
    agent_id VARCHAR(100) COMMENT '代理人ID',
    agent_type INT COMMENT '代理人类型',
    ext LONGTEXT COMMENT '扩展json',
    PRIMARY KEY (id),
    INDEX idx_task_actor_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务参与者表';

-- 历史任务参与者表
CREATE TABLE IF NOT EXISTS flw_his_task_actor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    actor_id VARCHAR(100) NOT NULL COMMENT '参与者ID',
    actor_name VARCHAR(100) NOT NULL COMMENT '参与者名称',
    actor_type INT NOT NULL COMMENT '参与者类型 0，用户 1，角色 2，部门',
    weight INT COMMENT '权重',
    agent_id VARCHAR(100) COMMENT '代理人ID',
    agent_type INT COMMENT '代理人类型',
    ext LONGTEXT COMMENT '扩展json',
    PRIMARY KEY (id),
    INDEX idx_his_task_actor_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史任务参与者表';

-- 扩展流程实例表
CREATE TABLE IF NOT EXISTS flw_ext_instance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    process_id BIGINT NOT NULL COMMENT '流程定义ID',
    process_name VARCHAR(100) COMMENT '流程名称',
    process_type VARCHAR(100) COMMENT '流程类型',
    model_content LONGTEXT COMMENT '流程模型定义JSON内容',
    PRIMARY KEY (id),
    CONSTRAINT fk_ext_instance_id FOREIGN KEY (id) REFERENCES flw_his_instance (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扩展流程实例表';
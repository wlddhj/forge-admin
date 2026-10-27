-- H2 端到端测试建表脚本（flw_ 8 表自 flowlong-mysql.sql 转换 + wf_/sys_ 精简）
-- 约定：id 不用自增（引擎/MP 雪花生成），列类型转 H2 兼容（text→CLOB、tinyint→TINYINT）

CREATE TABLE IF NOT EXISTS flw_process (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    create_id VARCHAR(50) NOT NULL,
    create_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    process_key VARCHAR(100) NOT NULL,
    process_name VARCHAR(100) NOT NULL,
    process_icon VARCHAR(255) DEFAULT NULL,
    process_type VARCHAR(100),
    process_version INT NOT NULL DEFAULT 1,
    instance_url VARCHAR(200),
    remark VARCHAR(255),
    use_scope TINYINT NOT NULL DEFAULT 0,
    process_state TINYINT NOT NULL DEFAULT 1,
    model_content CLOB,
    sort TINYINT DEFAULT 0,
    update_time TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS flw_instance (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    create_id VARCHAR(50) NOT NULL,
    create_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    process_id BIGINT NOT NULL,
    parent_instance_id BIGINT,
    priority TINYINT,
    instance_no VARCHAR(50),
    business_key VARCHAR(100),
    variable CLOB,
    current_node_name VARCHAR(100) NOT NULL,
    current_node_key VARCHAR(100) NOT NULL,
    expire_time TIMESTAMP NULL,
    last_update_by VARCHAR(50),
    last_update_time TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS flw_his_instance (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    create_id VARCHAR(50) NOT NULL,
    create_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    process_id BIGINT NOT NULL,
    parent_instance_id BIGINT,
    priority TINYINT,
    instance_no VARCHAR(50),
    business_key VARCHAR(100),
    variable CLOB,
    current_node_name VARCHAR(100) NOT NULL,
    current_node_key VARCHAR(100) NOT NULL,
    expire_time TIMESTAMP NULL,
    last_update_by VARCHAR(50),
    last_update_time TIMESTAMP NULL,
    instance_state TINYINT NOT NULL DEFAULT 0,
    end_time TIMESTAMP NULL,
    duration BIGINT
);

CREATE TABLE IF NOT EXISTS flw_task (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    create_id VARCHAR(50) NOT NULL,
    create_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    instance_id BIGINT NOT NULL,
    parent_task_id BIGINT,
    task_name VARCHAR(100) NOT NULL,
    task_key VARCHAR(100) NOT NULL,
    task_type TINYINT NOT NULL,
    perform_type TINYINT,
    action_url VARCHAR(200),
    variable CLOB,
    assignor_id VARCHAR(100),
    assignor VARCHAR(255),
    expire_time TIMESTAMP NULL,
    remind_time TIMESTAMP NULL,
    remind_repeat TINYINT NOT NULL DEFAULT 0,
    viewed TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS flw_his_task (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    create_id VARCHAR(50) NOT NULL,
    create_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    instance_id BIGINT NOT NULL,
    parent_task_id BIGINT,
    call_process_id BIGINT,
    call_instance_id BIGINT,
    task_name VARCHAR(100) NOT NULL,
    task_key VARCHAR(100) NOT NULL,
    task_type TINYINT NOT NULL,
    perform_type TINYINT,
    action_url VARCHAR(200),
    variable CLOB,
    assignor_id VARCHAR(100),
    assignor VARCHAR(255),
    expire_time TIMESTAMP NULL,
    remind_time TIMESTAMP NULL,
    remind_repeat TINYINT NOT NULL DEFAULT 0,
    viewed TINYINT NOT NULL DEFAULT 0,
    finish_time TIMESTAMP NULL,
    task_state TINYINT NOT NULL DEFAULT 0,
    duration BIGINT
);

CREATE TABLE IF NOT EXISTS flw_task_actor (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    instance_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    actor_type INT NOT NULL,
    weight INT,
    agent_id VARCHAR(100),
    agent_type INT,
    ext CLOB
);

CREATE TABLE IF NOT EXISTS flw_his_task_actor (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    instance_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    actor_type INT NOT NULL,
    weight INT,
    agent_id VARCHAR(100),
    agent_type INT,
    ext CLOB
);

CREATE TABLE IF NOT EXISTS flw_ext_instance (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    create_id VARCHAR(50),
    create_by VARCHAR(50),
    create_time TIMESTAMP,
    process_id BIGINT NOT NULL,
    process_name VARCHAR(100),
    process_type VARCHAR(100),
    model_content CLOB
);

-- ===== forge 工作流扩展表（测试用列） =====
CREATE TABLE IF NOT EXISTS wf_approval_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    process_instance_id BIGINT,
    task_id BIGINT,
    task_def_key VARCHAR(100),
    task_name VARCHAR(255),
    user_id BIGINT,
    user_name VARCHAR(100),
    action_type VARCHAR(50),
    comment_text VARCHAR(1000),
    attachment_ids VARCHAR(500),
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_event_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    instance_id BIGINT,
    task_id BIGINT,
    task_name VARCHAR(255),
    operator_id VARCHAR(64),
    operator_name VARCHAR(100),
    summary VARCHAR(1000),
    create_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_business_callback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(500) NOT NULL,
    secret VARCHAR(100),
    events VARCHAR(200) NOT NULL,
    process_key VARCHAR(100),
    include_variables TINYINT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    timeout_ms INT DEFAULT 5000,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_callback_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    callback_id BIGINT NOT NULL,
    callback_name VARCHAR(100),
    event_type VARCHAR(30) NOT NULL,
    process_key VARCHAR(100),
    business_key VARCHAR(100),
    instance_id BIGINT,
    task_id BIGINT,
    node_key VARCHAR(100),
    payload TEXT,
    http_status INT,
    response_body VARCHAR(2000),
    status VARCHAR(10) NOT NULL,
    retry_count INT DEFAULT 0,
    error_msg VARCHAR(500),
    create_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_ai_route_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    process_instance_id BIGINT NOT NULL,
    gateway_node_key VARCHAR(100) NOT NULL,
    gateway_node_name VARCHAR(200),
    gateway_type INT,
    candidate_branches TEXT,
    selected_branches VARCHAR(500),
    confidence INT,
    reasoning TEXT,
    raw_response TEXT,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(50),
    model_name VARCHAR(100),
    create_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_process_ext (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_id BIGINT,
    process_key VARCHAR(100),
    process_name VARCHAR(255),
    category_id BIGINT,
    description VARCHAR(500),
    form_type INT,
    form_id BIGINT,
    auto_copy_strategy INT,
    auto_copy_param VARCHAR(512),
    use_scope INT DEFAULT 0,
    use_scope_users VARCHAR(2000),
    model_json CLOB,
    meta_info CLOB,
    create_by BIGINT,
    create_by_name VARCHAR(100),
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- ===== sys 精简（身份桥接所需列） =====
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    username VARCHAR(50),
    nickname VARCHAR(100),
    password VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    avatar VARCHAR(255),
    dept_id BIGINT,
    position_id BIGINT,
    account_type INT,
    status INT DEFAULT 1,
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(50),
    password_update_time TIMESTAMP,
    first_login INT,
    password_error_count INT,
    lock_time TIMESTAMP,
    phone_suffix VARCHAR(20),
    email_suffix VARCHAR(100),
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY,
    role_name VARCHAR(50),
    role_code VARCHAR(50),
    status INT DEFAULT 1,
    deleted TINYINT DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    user_id BIGINT,
    role_id BIGINT
);
CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dept_name VARCHAR(50),
    parent_id BIGINT DEFAULT 0,
    ancestors VARCHAR(200),
    leader VARCHAR(50),
    sort_order INT DEFAULT 0,
    deleted TINYINT DEFAULT 0
);

-- 种子：三个测试用户（审批人解析/身份桥接）；先清理保证跨测试类幂等
DELETE FROM sys_user;
INSERT INTO sys_user (id, username, nickname, dept_id) VALUES
(1, 'admin', '发起人甲', 100),
(2, 'user1', '审批人乙', 100),
(3, 'user2', '审批人丙', 100);
DELETE FROM sys_dept;
INSERT INTO sys_dept (id, dept_name, parent_id, ancestors) VALUES (100, '测试部门', 0, '0');

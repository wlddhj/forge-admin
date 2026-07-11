-- 租户主表
CREATE TABLE IF NOT EXISTS sys_tenant (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64)   NOT NULL COMMENT '租户名称',
  code            VARCHAR(32)   NOT NULL UNIQUE COMMENT '租户标识（登录用）',
  contact_name    VARCHAR(32)   COMMENT '联系人',
  contact_phone   VARCHAR(32)   COMMENT '联系电话',
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  package_id      BIGINT        COMMENT '套餐ID',
  expire_time     DATETIME      COMMENT '到期时间（NULL=永久）',
  website         VARCHAR(255)  COMMENT '租户官网',
  remark          VARCHAR(500)  COMMENT '备注',
  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by       BIGINT,
  update_by       BIGINT,
  deleted         TINYINT       NOT NULL DEFAULT 0,
  INDEX idx_status (status),
  INDEX idx_package (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- 租户套餐
CREATE TABLE IF NOT EXISTS sys_tenant_package (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64)   NOT NULL,
  code            VARCHAR(32)   NOT NULL UNIQUE,
  status          TINYINT       NOT NULL DEFAULT 1,
  remark          VARCHAR(500),
  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by       BIGINT,
  update_by       BIGINT,
  deleted         TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐';

-- 套餐-菜单
CREATE TABLE IF NOT EXISTS sys_tenant_package_menu (
  tenant_package_id  BIGINT NOT NULL,
  menu_id            BIGINT NOT NULL,
  PRIMARY KEY (tenant_package_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐-菜单关联';

-- 套餐-角色
CREATE TABLE IF NOT EXISTS sys_tenant_package_role (
  tenant_package_id BIGINT NOT NULL,
  role_id           BIGINT NOT NULL,
  PRIMARY KEY (tenant_package_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐-角色关联';

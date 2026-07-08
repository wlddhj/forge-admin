-- ========================================
-- 大屏访问授权字段
-- ========================================
-- 背景：支持大屏公开访问（iframe 嵌入/H5 公开大屏）和细粒度授权。
-- 字段说明：
--   is_public: 0=需登录（默认）1=公开访问（无需登录）
--   access_type: 0=登录可访问 1=指定角色可访问（当前版本未实现细粒度，保留扩展位）

ALTER TABLE sys_screen
    ADD COLUMN is_public INT NOT NULL DEFAULT 0 COMMENT '是否公开：0=登录可访问 1=公开访问（无需登录）' AFTER status,
    ADD COLUMN access_type INT NOT NULL DEFAULT 0 COMMENT '访问授权类型：0=登录可访问 1=指定角色可访问' AFTER is_public;

-- 扩展 phone 列宽以容纳 AES-256-GCM 加密后密文
-- 密文格式: ENCv1:{base64(iv(12) + ciphertext(N) + tag(16))}
-- 最短: 6(前缀) + ceil((12+1+16)/3)*4 = 6 + 40 = 46 字符
-- 手机号 11 位明文: 6 + ceil((12+11+16)/3)*4 = 6 + 52 = 58 字符
-- VARCHAR(20) 完全不足，扩展到 VARCHAR(255) 兼容未来加密字段

ALTER TABLE `sys_user` MODIFY COLUMN `phone` VARCHAR(255) DEFAULT NULL COMMENT '手机号（加密存储）';
ALTER TABLE `app_user` MODIFY COLUMN `phone` VARCHAR(255) DEFAULT NULL COMMENT '手机号';
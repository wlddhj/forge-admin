package com.forge.modules.system.service.storage;

/**
 * 附件访问 URL 归一化助手
 */
public final class AttachmentUrls {

    private AttachmentUrls() {
    }

    /**
     * App 侧公开直链：local 存储的 domain 可能被配置为网关聚合路径（如 https://host/api/uploads），
     * App 端不走该网关代理，须改写为后端直连路径 /uploads/**（SecurityConfig 与租户闸均公开可读）。
     * 非 /api/uploads/ 前缀的 URL（对象存储域名直链等）原样返回。
     */
    public static String appPublicUrl(String fileUrl) {
        if (fileUrl != null && fileUrl.contains("/api/uploads/")) {
            return fileUrl.replace("/api/uploads/", "/uploads/");
        }
        return fileUrl;
    }
}

package com.forge.admin.modules.system.dto.file;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件存储配置响应
 *
 * @author standadmin
 */
@Data
public class FileConfigResponse {

    private Long id;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 存储类型
     */
    private String storageType;

    /**
     * 服务端点
     */
    private String endpoint;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * AccessKey（脱敏）
     */
    private String accessKey;

    /**
     * SecretKey（不返回）
     */
    // private String secretKey;

    /**
     * 自定义域名
     */
    private String domain;

    /**
     * 基础路径
     */
    private String basePath;

    /**
     * 是否默认
     */
    private Integer isDefault;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

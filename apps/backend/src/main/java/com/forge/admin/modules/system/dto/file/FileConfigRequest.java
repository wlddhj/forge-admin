package com.forge.admin.modules.system.dto.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件存储配置请求
 *
 * @author standadmin
 */
@Data
public class FileConfigRequest {

    private Long id;

    /**
     * 配置名称
     */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称长度不能超过100")
    private String configName;

    /**
     * 存储类型(local/aliyun_oss/tencent_cos/minio)
     */
    @NotBlank(message = "存储类型不能为空")
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
     * AccessKey
     */
    private String accessKey;

    /**
     * SecretKey
     */
    private String secretKey;

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
}

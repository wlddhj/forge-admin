package com.forge.admin.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件存储配置实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_file_config")
public class SysFileConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 存储类型(local/aliyun_oss/tencent_cos/minio)
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
     * 是否默认(1:是 0:否)
     */
    private Integer isDefault;

    /**
     * 状态(1:启用 0:禁用)
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 删除标记
     */
    @TableLogic
    private Integer deleted;
}

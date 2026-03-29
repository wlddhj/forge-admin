package com.forge.admin.modules.system.dto.file;

import lombok.Data;

/**
 * 文件存储配置查询请求
 *
 * @author standadmin
 */
@Data
public class FileConfigQueryRequest {

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 10;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 存储类型
     */
    private String storageType;

    /**
     * 状态
     */
    private Integer status;
}

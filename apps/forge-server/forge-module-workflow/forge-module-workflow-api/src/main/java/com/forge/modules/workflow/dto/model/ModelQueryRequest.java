package com.forge.modules.workflow.dto.model;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 模型查询请求
 *
 * @author forge-admin
 */
@Data
public class ModelQueryRequest {

    /**
     * 模型名称（模糊查询）
     */
    private String name;

    /**
     * 模型标识
     */
    private String key;

    /**
     * 分类编码
     */
    private String category;

    /**
     * 当前页
     */
    @Min(1)
    private int pageNum = 1;

    /**
     * 每页大小
     */
    @Min(1)
    private int pageSize = 20;
}

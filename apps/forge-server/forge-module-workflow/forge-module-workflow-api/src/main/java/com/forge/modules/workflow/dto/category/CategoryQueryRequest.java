package com.forge.modules.workflow.dto.category;

import lombok.Data;

/**
 * 流程分类查询请求
 *
 * @author forge
 */
@Data
public class CategoryQueryRequest {

    /**
     * 分类名称（模糊查询）
     */
    private String categoryName;

    /**
     * 分类编码
     */
    private String categoryCode;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status;

    /**
     * 当前页
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;
}

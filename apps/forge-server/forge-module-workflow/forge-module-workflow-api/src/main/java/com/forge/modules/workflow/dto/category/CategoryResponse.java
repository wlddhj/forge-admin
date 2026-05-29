package com.forge.modules.workflow.dto.category;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程分类响应
 *
 * @author forge
 */
@Data
public class CategoryResponse {

    private Long id;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 分类编码
     */
    private String categoryCode;

    /**
     * 父级ID
     */
    private Long parentId;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态(0:禁用 1:启用)
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

    /**
     * 子分类列表（树形结构）
     */
    private List<CategoryResponse> children;
}

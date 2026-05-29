package com.forge.modules.workflow.dto.category;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程分类请求
 *
 * @author forge
 */
@Data
public class CategoryRequest {

    /**
     * 分类ID（更新时必传）
     */
    private Long id;

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;

    /**
     * 分类编码
     */
    @NotBlank(message = "分类编码不能为空")
    private String categoryCode;

    /**
     * 父级ID
     */
    private Long parentId;

    /**
     * 排序
     */
    private Integer sortOrder = 0;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status = 1;

    /**
     * 备注
     */
    private String remark;
}

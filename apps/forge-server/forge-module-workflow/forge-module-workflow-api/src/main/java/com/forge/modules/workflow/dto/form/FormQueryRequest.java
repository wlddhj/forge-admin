package com.forge.modules.workflow.dto.form;

import lombok.Data;

/**
 * 表单查询请求
 *
 * @author forge
 */
@Data
public class FormQueryRequest {

    /**
     * 表单名称（模糊查询）
     */
    private String name;

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

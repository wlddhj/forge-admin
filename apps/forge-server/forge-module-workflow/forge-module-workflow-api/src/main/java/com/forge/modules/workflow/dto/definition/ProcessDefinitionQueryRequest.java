package com.forge.modules.workflow.dto.definition;

import lombok.Data;

/**
 * 流程定义查询请求
 *
 * @author forge-admin
 */
@Data
public class ProcessDefinitionQueryRequest {

    /**
     * 当前页码
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 流程名称（模糊查询）
     */
    private String name;

    /**
     * 流程Key（精确查询）
     */
    private String key;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 挂起状态（1激活 2挂起）
     */
    private Integer suspensionState;
}

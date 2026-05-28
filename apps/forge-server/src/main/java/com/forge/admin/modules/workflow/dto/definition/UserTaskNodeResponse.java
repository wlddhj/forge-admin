package com.forge.admin.modules.workflow.dto.definition;

import lombok.Data;

/**
 * 用户任务节点信息（发起人自选用）
 */
@Data
public class UserTaskNodeResponse {

    /** 任务定义Key */
    private String taskDefKey;

    /** 任务名称 */
    private String taskName;

    /** 候选人策略 */
    private Integer candidateStrategy;
}

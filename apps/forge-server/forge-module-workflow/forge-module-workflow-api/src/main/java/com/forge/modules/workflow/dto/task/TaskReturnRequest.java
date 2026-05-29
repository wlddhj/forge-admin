package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任务退回请求
 *
 * @author forge-admin
 */
@Data
public class TaskReturnRequest {

    /**
     * 退回到目标节点Key
     */
    @NotBlank(message = "目标节点Key不能为空")
    private String targetTaskDefKey;

    /**
     * 退回意见
     */
    private String comment;
}

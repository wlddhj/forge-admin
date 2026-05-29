package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务转办请求
 *
 * @author forge-admin
 */
@Data
public class TaskTransferRequest {

    /**
     * 转办目标用户ID
     */
    @NotNull(message = "转办目标用户ID不能为空")
    private Long transferUserId;

    /**
     * 转办意见
     */
    private String comment;
}

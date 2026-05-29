package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务委派请求
 *
 * @author forge-admin
 */
@Data
public class TaskDelegateRequest {

    /**
     * 被委派人ID
     */
    @NotNull(message = "被委派人ID不能为空")
    private Long delegateUserId;

    /**
     * 委派意见
     */
    private String comment;
}

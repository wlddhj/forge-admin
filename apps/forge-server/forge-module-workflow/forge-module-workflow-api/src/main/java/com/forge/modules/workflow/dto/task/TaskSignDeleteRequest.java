package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskSignDeleteRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /** 被减签的用户ID */
    @NotBlank(message = "被减签的用户不能为空")
    private String actorId;

    private String reason;
}

package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskSignDeleteRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotBlank(message = "被减签的任务ID不能为空")
    private String childTaskId;

    private String reason;
}

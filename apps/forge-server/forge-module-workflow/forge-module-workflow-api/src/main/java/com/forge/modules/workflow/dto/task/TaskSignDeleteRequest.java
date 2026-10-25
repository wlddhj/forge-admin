package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskSignDeleteRequest {

    /**
     * 任务ID（由 Controller 以路径变量填充，body 无需携带）
     */
    private String taskId;

    /** 被减签的用户ID */
    @NotBlank(message = "被减签的用户不能为空")
    private String actorId;

    private String reason;
}

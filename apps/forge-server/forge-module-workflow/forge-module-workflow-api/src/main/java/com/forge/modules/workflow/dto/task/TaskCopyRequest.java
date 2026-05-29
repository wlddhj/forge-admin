package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TaskCopyRequest {

    private String taskId;

    @NotEmpty(message = "抄送用户列表不能为空")
    private List<Long> copyUserIds;

    private String reason;
}

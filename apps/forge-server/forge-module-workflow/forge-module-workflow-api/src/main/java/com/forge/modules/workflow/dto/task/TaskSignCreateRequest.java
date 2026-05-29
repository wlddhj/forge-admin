package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TaskSignCreateRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotBlank(message = "加签类型不能为空")
    private String type;

    @NotNull(message = "加签用户列表不能为空")
    private List<String> userIds;

    private String reason;
}

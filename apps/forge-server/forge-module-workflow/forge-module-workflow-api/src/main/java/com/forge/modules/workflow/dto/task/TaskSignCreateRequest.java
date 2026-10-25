package com.forge.modules.workflow.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TaskSignCreateRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * 加签类型：before 前加签（插临时节点，加签人先审后回到本节点）/ after 后加签（插临时节点，本节点审完进入）/ parallel 并行加签（当前节点内追加参与人）
     */
    @NotBlank(message = "加签类型不能为空")
    private String type;

    @NotNull(message = "加签用户列表不能为空")
    private List<String> userIds;

    /**
     * 多人审批方式（仅 before/after 生效）：1 依次 / 2 会签（默认）/ 3 或签
     */
    private Integer examineMode;

    private String reason;
}

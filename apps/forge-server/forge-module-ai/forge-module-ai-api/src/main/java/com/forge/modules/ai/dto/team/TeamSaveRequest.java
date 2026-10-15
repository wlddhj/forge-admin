package com.forge.modules.ai.dto.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 团队保存请求（新增/更新同构，成员整体重建）
 */
@Data
public class TeamSaveRequest {

    @NotBlank(message = "团队编码不能为空")
    @Size(max = 50, message = "团队编码最长 50 字符")
    private String teamCode;

    @NotBlank(message = "团队名称不能为空")
    @Size(max = 100, message = "团队名称最长 100 字符")
    private String teamName;

    @Size(max = 500, message = "团队描述最长 500 字符")
    private String description;

    @NotBlank(message = "Supervisor 提示词不能为空")
    private String supervisorPrompt;

    /** {"supervisor":{modelId|modelType},"summary":{...}} */
    private String modelBindings;

    @NotNull(message = "最大调度轮次不能为空")
    private Integer maxRounds;

    private Integer status;

    private String remark;

    @NotEmpty(message = "团队至少需要一个成员")
    @Valid
    private List<TeamMemberItem> members;
}

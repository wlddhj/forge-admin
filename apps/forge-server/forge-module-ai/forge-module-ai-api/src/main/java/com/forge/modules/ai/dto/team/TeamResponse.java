package com.forge.modules.ai.dto.team;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 团队响应（含成员聚合）
 */
@Data
public class TeamResponse {

    private Long id;

    private String teamCode;

    private String teamName;

    private String description;

    private String supervisorPrompt;

    private String modelBindings;

    private Integer maxRounds;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private List<TeamMemberItem> members;
}

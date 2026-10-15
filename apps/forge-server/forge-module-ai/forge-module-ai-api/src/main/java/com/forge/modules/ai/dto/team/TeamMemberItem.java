package com.forge.modules.ai.dto.team;

import lombok.Data;

import java.util.List;

/**
 * 团队成员项（保存入参 + 回显，agentCode/agentName 仅回显用）
 */
@Data
public class TeamMemberItem {

    private Long agentId;

    private String agentCode;

    private String agentName;

    private String memberRole;

    /** 工具白名单（空=继承全部工具） */
    private List<String> allowedTools;

    /** 专家内最大循环步数（NULL=取 Agent 配置） */
    private Integer maxSteps;

    private Integer sortOrder;
}

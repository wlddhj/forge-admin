package com.forge.modules.ai.service;

import com.forge.modules.ai.dto.team.TeamResponse;
import com.forge.modules.ai.dto.team.TeamSaveRequest;
import com.forge.modules.ai.entity.AiAgentTeam;
import com.forge.modules.ai.entity.AiAgentTeamMember;

import java.util.List;

/**
 * Agent 团队管理：CRUD + 成员聚合 + 运行时加载
 */
public interface AiAgentTeamService {

    List<TeamResponse> list(Integer status);

    TeamResponse get(Long id);

    void add(TeamSaveRequest request);

    void update(Long id, TeamSaveRequest request);

    void delete(List<Long> ids);

    /** 按编码取启用团队（对话/定时任务入口用），不存在或未启用抛 BusinessException */
    AiAgentTeam getEnabledByCode(String teamCode);

    /** 团队成员（按 sort_order 升序），并校验成员引用的 Agent 均启用 */
    List<AiAgentTeamMember> loadEnabledMembers(Long teamId);
}

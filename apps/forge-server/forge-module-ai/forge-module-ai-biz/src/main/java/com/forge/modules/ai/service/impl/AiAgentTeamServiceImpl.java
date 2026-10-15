package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.dto.team.TeamMemberItem;
import com.forge.modules.ai.dto.team.TeamResponse;
import com.forge.modules.ai.dto.team.TeamSaveRequest;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentTeam;
import com.forge.modules.ai.entity.AiAgentTeamMember;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMemberMapper;
import com.forge.modules.ai.service.AiAgentTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAgentTeamServiceImpl implements AiAgentTeamService {

    private final AiAgentTeamMapper teamMapper;
    private final AiAgentTeamMemberMapper memberMapper;
    private final AiAgentConfigMapper agentConfigMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<TeamResponse> list(Integer status) {
        List<AiAgentTeam> teams = teamMapper.selectList(new LambdaQueryWrapper<AiAgentTeam>()
                .eq(status != null, AiAgentTeam::getStatus, status)
                .orderByAsc(AiAgentTeam::getId));
        if (teams.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = teams.stream().map(AiAgentTeam::getId).toList();
        Map<Long, List<AiAgentTeamMember>> membersByTeam = memberMapper.selectList(
                        new LambdaQueryWrapper<AiAgentTeamMember>()
                                .in(AiAgentTeamMember::getTeamId, teamIds)
                                .orderByAsc(AiAgentTeamMember::getSortOrder))
                .stream().collect(Collectors.groupingBy(AiAgentTeamMember::getTeamId));
        Map<Long, AiAgentConfig> agents = loadAgents(membersByTeam.values().stream()
                .flatMap(List::stream).toList());
        return teams.stream().map(t -> toResponse(t,
                membersByTeam.getOrDefault(t.getId(), List.of()), agents)).toList();
    }

    @Override
    public TeamResponse get(Long id) {
        AiAgentTeam team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException("团队不存在");
        }
        List<AiAgentTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<AiAgentTeamMember>()
                        .eq(AiAgentTeamMember::getTeamId, id)
                        .orderByAsc(AiAgentTeamMember::getSortOrder));
        return toResponse(team, members, loadAgents(members));
    }

    @Override
    @Transactional
    public void add(TeamSaveRequest request) {
        checkCodeUnique(null, request.getTeamCode());
        validateMembers(request.getMembers());
        AiAgentTeam team = new AiAgentTeam();
        copy(team, request);
        teamMapper.insert(team);
        insertMembers(team.getId(), request.getMembers());
    }

    @Override
    @Transactional
    public void update(Long id, TeamSaveRequest request) {
        AiAgentTeam existing = teamMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("团队不存在");
        }
        checkCodeUnique(id, request.getTeamCode());
        validateMembers(request.getMembers());
        copy(existing, request);
        teamMapper.updateById(existing);
        memberMapper.delete(new LambdaQueryWrapper<AiAgentTeamMember>()
                .eq(AiAgentTeamMember::getTeamId, id));
        insertMembers(id, request.getMembers());
    }

    @Override
    @Transactional
    public void delete(List<Long> ids) {
        for (Long id : ids) {
            memberMapper.delete(new LambdaQueryWrapper<AiAgentTeamMember>()
                    .eq(AiAgentTeamMember::getTeamId, id));
            teamMapper.deleteById(id);
        }
    }

    @Override
    public AiAgentTeam getEnabledByCode(String teamCode) {
        AiAgentTeam team = teamMapper.selectOne(new LambdaQueryWrapper<AiAgentTeam>()
                .eq(AiAgentTeam::getTeamCode, teamCode)
                .eq(AiAgentTeam::getStatus, 1)
                .last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException("Agent 团队不存在或未启用: " + teamCode);
        }
        return team;
    }

    @Override
    public List<AiAgentTeamMember> loadEnabledMembers(Long teamId) {
        List<AiAgentTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<AiAgentTeamMember>()
                        .eq(AiAgentTeamMember::getTeamId, teamId)
                        .orderByAsc(AiAgentTeamMember::getSortOrder));
        if (members.isEmpty()) {
            throw new BusinessException("团队没有配置成员，无法执行协作");
        }
        for (AiAgentTeamMember member : members) {
            AiAgentConfig agent = agentConfigMapper.selectById(member.getAgentId());
            if (agent == null || agent.getStatus() == null || agent.getStatus() != 1) {
                throw new BusinessException("团队成员引用的 Agent 不存在或未启用: " + member.getMemberRole());
            }
        }
        return members;
    }

    // ========== 内部 ==========

    private void checkCodeUnique(Long excludeId, String teamCode) {
        AiAgentTeam existing = teamMapper.selectOne(new LambdaQueryWrapper<AiAgentTeam>()
                .eq(AiAgentTeam::getTeamCode, teamCode)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("团队编码已存在: " + teamCode);
        }
    }

    private void validateMembers(List<TeamMemberItem> members) {
        Set<Long> agentIds = new HashSet<>();
        for (TeamMemberItem member : members) {
            if (member.getAgentId() == null) {
                throw new BusinessException("成员必须选择 Agent");
            }
            if (member.getMemberRole() == null || member.getMemberRole().isBlank()) {
                throw new BusinessException("成员职责描述不能为空");
            }
            if (!agentIds.add(member.getAgentId())) {
                throw new BusinessException("同一 Agent 不允许重复加入团队");
            }
            AiAgentConfig agent = agentConfigMapper.selectById(member.getAgentId());
            if (agent == null || agent.getStatus() == null || agent.getStatus() != 1) {
                throw new BusinessException("成员引用的 Agent 不存在或未启用: " + member.getAgentId());
            }
        }
    }

    private void copy(AiAgentTeam team, TeamSaveRequest request) {
        team.setTeamCode(request.getTeamCode());
        team.setTeamName(request.getTeamName());
        team.setDescription(request.getDescription());
        team.setSupervisorPrompt(request.getSupervisorPrompt());
        team.setModelBindings(request.getModelBindings());
        team.setMaxRounds(request.getMaxRounds());
        team.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        team.setRemark(request.getRemark());
    }

    private void insertMembers(Long teamId, List<TeamMemberItem> members) {
        int sort = 0;
        for (TeamMemberItem item : members) {
            AiAgentTeamMember member = new AiAgentTeamMember();
            member.setTeamId(teamId);
            member.setAgentId(item.getAgentId());
            member.setMemberRole(item.getMemberRole());
            member.setAllowedTools(toolsToJson(item.getAllowedTools()));
            member.setMaxSteps(item.getMaxSteps());
            member.setSortOrder(item.getSortOrder() == null ? sort : item.getSortOrder());
            memberMapper.insert(member);
            sort++;
        }
    }

    private String toolsToJson(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (Exception e) {
            throw new BusinessException("工具白名单序列化失败");
        }
    }

    private List<String> jsonToTools(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<Long, AiAgentConfig> loadAgents(List<AiAgentTeamMember> members) {
        if (members.isEmpty()) {
            return Map.of();
        }
        List<Long> agentIds = members.stream().map(AiAgentTeamMember::getAgentId).distinct().toList();
        return agentConfigMapper.selectBatchIds(agentIds).stream()
                .collect(Collectors.toMap(AiAgentConfig::getId, Function.identity()));
    }

    private TeamResponse toResponse(AiAgentTeam team, List<AiAgentTeamMember> members,
                                    Map<Long, AiAgentConfig> agents) {
        TeamResponse response = new TeamResponse();
        response.setId(team.getId());
        response.setTeamCode(team.getTeamCode());
        response.setTeamName(team.getTeamName());
        response.setDescription(team.getDescription());
        response.setSupervisorPrompt(team.getSupervisorPrompt());
        response.setModelBindings(team.getModelBindings());
        response.setMaxRounds(team.getMaxRounds());
        response.setStatus(team.getStatus());
        response.setRemark(team.getRemark());
        response.setCreateTime(team.getCreateTime());
        List<TeamMemberItem> items = new ArrayList<>();
        for (AiAgentTeamMember member : members) {
            TeamMemberItem item = new TeamMemberItem();
            item.setAgentId(member.getAgentId());
            AiAgentConfig agent = agents.get(member.getAgentId());
            if (agent != null) {
                item.setAgentCode(agent.getAgentCode());
                item.setAgentName(agent.getAgentName());
            }
            item.setMemberRole(member.getMemberRole());
            item.setAllowedTools(jsonToTools(member.getAllowedTools()));
            item.setMaxSteps(member.getMaxSteps());
            item.setSortOrder(member.getSortOrder());
            items.add(item);
        }
        response.setMembers(items);
        return response;
    }
}

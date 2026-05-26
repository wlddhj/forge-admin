package com.forge.admin.modules.workflow.framework.candidate.strategy;

import com.forge.admin.modules.system.mapper.SysUserMapper;
import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色候选人策略
 * 根据角色ID查询拥有该角色的用户
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleCandidateStrategy implements BpmTaskCandidateStrategy {

    private final SysUserMapper sysUserMapper;

    @Override
    public int getStrategy() {
        return 10;
    }

    @Override
    public String getDescription() {
        return "指定角色";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            Set<Long> roleIds = Arrays.stream(param.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
            if (roleIds.isEmpty()) {
                return Collections.emptySet();
            }
            Set<Long> userIds = sysUserMapper.selectUserIdsByRoleIds(roleIds);
            log.debug("角色候选人计算完成: roleIds={}, userIds={}", roleIds, userIds);
            return userIds;
        } catch (NumberFormatException e) {
            log.warn("角色候选人参数解析失败: param={}", param, e);
            return Collections.emptySet();
        }
    }
}
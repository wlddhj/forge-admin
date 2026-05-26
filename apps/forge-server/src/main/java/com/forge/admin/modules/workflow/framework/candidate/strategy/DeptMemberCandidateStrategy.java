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
 * 部门成员候选人策略
 * 根据部门ID查询该部门下的所有用户
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeptMemberCandidateStrategy implements BpmTaskCandidateStrategy {

    private final SysUserMapper sysUserMapper;

    @Override
    public int getStrategy() {
        return 20;
    }

    @Override
    public String getDescription() {
        return "部门成员";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            Set<Long> deptIds = Arrays.stream(param.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
            if (deptIds.isEmpty()) {
                return Collections.emptySet();
            }
            Set<Long> userIds = sysUserMapper.selectUserIdsByDeptIds(deptIds);
            log.debug("部门成员候选人计算完成: deptIds={}, userIds={}", deptIds, userIds);
            return userIds;
        } catch (NumberFormatException e) {
            log.warn("部门成员候选人参数解析失败: param={}", param, e);
            return Collections.emptySet();
        }
    }
}
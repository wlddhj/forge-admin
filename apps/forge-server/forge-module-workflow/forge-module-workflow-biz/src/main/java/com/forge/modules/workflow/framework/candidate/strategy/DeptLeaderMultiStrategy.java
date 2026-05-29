package com.forge.modules.workflow.framework.candidate.strategy;

import com.forge.modules.system.entity.SysDept;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysDeptMapper;
import com.forge.modules.system.mapper.SysUserMapper;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.framework.candidate.CandidateStrategyEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 连续多级部门负责人候选人策略
 * 参数格式：部门ID:层级数（如 "1:2" 表示从部门1开始向上2级负责人）
 * 也支持多个：部门ID:层级数,部门ID:层级数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeptLeaderMultiStrategy implements BpmTaskCandidateStrategy {

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.DEPT_LEADER_MULTI.getCode();
    }

    @Override
    public String getDescription() {
        return "连续多级部门负责人";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            Set<Long> userIds = new HashSet<>();
            String[] pairs = param.split(",");
            for (String pair : pairs) {
                String[] parts = pair.trim().split(":");
                if (parts.length != 2) continue;

                Long deptId = Long.parseLong(parts[0].trim());
                int level = Integer.parseInt(parts[1].trim());

                Set<Long> leaders = getDeptLeaders(deptId, level);
                userIds.addAll(leaders);
            }
            return userIds;
        } catch (Exception e) {
            log.warn("连续多级部门负责人参数解析失败: param={}", param, e);
            return Collections.emptySet();
        }
    }

    private Set<Long> getDeptLeaders(Long deptId, int level) {
        Set<Long> leaders = new HashSet<>();
        Long currentDeptId = deptId;

        for (int i = 0; i < level && currentDeptId != null; i++) {
            SysDept dept = sysDeptMapper.selectById(currentDeptId);
            if (dept == null) break;

            if (dept.getLeader() != null && !dept.getLeader().isEmpty()) {
                SysUser leaderUser = sysUserMapper.selectByUsernameSimple(dept.getLeader());
                if (leaderUser != null) {
                    leaders.add(leaderUser.getId());
                }
            }

            // 向上级部门移动
            currentDeptId = dept.getParentId();
            if (currentDeptId == null || currentDeptId == 0L) break;
        }
        return leaders;
    }
}

package com.forge.admin.modules.workflow.framework.candidate.strategy;

import com.forge.admin.modules.system.entity.SysDept;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysDeptMapper;
import com.forge.admin.modules.system.mapper.SysUserMapper;
import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.admin.modules.workflow.framework.candidate.CandidateStrategyEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门负责人候选人策略
 * 根据部门ID查询该部门的负责人
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeptLeaderCandidateStrategy implements BpmTaskCandidateStrategy {

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.DEPT_LEADER.getCode();
    }

    @Override
    public String getDescription() {
        return "部门负责人";
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

            Set<Long> userIds = new HashSet<>();
            for (Long deptId : deptIds) {
                // 查询部门负责人
                // SysDept 的 leader 字段存储的是负责人用户名
                SysDept dept = sysDeptMapper.selectById(deptId);
                if (dept != null && dept.getLeader() != null && !dept.getLeader().isEmpty()) {
                    // leader 字段是用户名，需要转换为用户ID
                    SysUser leaderUser = sysUserMapper.selectByUsernameSimple(dept.getLeader());
                    if (leaderUser != null) {
                        userIds.add(leaderUser.getId());
                    }
                }
            }
            log.debug("部门负责人候选人计算完成: deptIds={}, userIds={}", deptIds, userIds);
            return userIds;
        } catch (NumberFormatException e) {
            log.warn("部门负责人候选人参数解析失败: param={}", param, e);
            return Collections.emptySet();
        }
    }
}
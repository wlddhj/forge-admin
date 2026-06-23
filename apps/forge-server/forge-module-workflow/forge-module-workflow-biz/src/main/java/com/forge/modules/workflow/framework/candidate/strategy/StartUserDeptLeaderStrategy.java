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

import java.util.Collections;
import java.util.Set;

/**
 * 发起人部门负责人候选人策略 - FlowLong 版本
 * 查询流程发起人所在部门的负责人
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartUserDeptLeaderStrategy implements BpmTaskCandidateStrategy {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.START_USER_DEPT_LEADER.getCode();
    }

    @Override
    public String getDescription() {
        return "发起人部门负责人";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        return Collections.emptySet();
    }

    @Override
    public Set<Long> calculateUsers(String param, TaskContext taskContext) {
        try {
            Long startUserId = taskContext.getStartUserId();
            if (startUserId == null) {
                return Collections.emptySet();
            }

            SysUser startUser = sysUserMapper.selectById(startUserId);
            if (startUser == null || startUser.getDeptId() == null) {
                return Collections.emptySet();
            }

            SysDept dept = sysDeptMapper.selectById(startUser.getDeptId());
            if (dept == null || dept.getLeader() == null || dept.getLeader().isEmpty()) {
                return Collections.emptySet();
            }

            SysUser leaderUser = sysUserMapper.selectByUsernameSimple(dept.getLeader());
            if (leaderUser != null) {
                log.debug("发起人部门负责人候选人: startUserId={}, deptId={}, leaderId={}",
                        startUserId, startUser.getDeptId(), leaderUser.getId());
                return Set.of(leaderUser.getId());
            }
        } catch (Exception e) {
            log.warn("获取发起人部门负责人失败: {}", e.getMessage());
        }
        return Collections.emptySet();
    }
}
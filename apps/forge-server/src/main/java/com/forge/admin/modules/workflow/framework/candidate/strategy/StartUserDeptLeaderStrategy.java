package com.forge.admin.modules.workflow.framework.candidate.strategy;

import com.forge.admin.modules.system.entity.SysDept;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysDeptMapper;
import com.forge.admin.modules.system.mapper.SysUserMapper;
import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 发起人部门负责人候选人策略
 * 查询流程发起人所在部门的负责人
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartUserDeptLeaderStrategy implements BpmTaskCandidateStrategy {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private RuntimeService runtimeService;

    @Autowired
    @Lazy
    public void setRuntimeService(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public int getStrategy() {
        return 37;
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
    public Set<Long> calculateUsers(String param, DelegateTask delegateTask) {
        try {
            Long startUserId = getStartUserId(delegateTask);
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
                return Set.of(leaderUser.getId());
            }
        } catch (Exception e) {
            log.warn("获取发起人部门负责人失败: {}", e.getMessage());
        }
        return Collections.emptySet();
    }

    private Long getStartUserId(DelegateTask delegateTask) {
        String processInstanceId = delegateTask.getProcessInstanceId();
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance != null && processInstance.getStartUserId() != null) {
            return Long.parseLong(processInstance.getStartUserId());
        }
        return null;
    }
}

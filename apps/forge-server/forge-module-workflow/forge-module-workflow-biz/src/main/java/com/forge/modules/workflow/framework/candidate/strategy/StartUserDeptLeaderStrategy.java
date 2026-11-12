package com.forge.modules.workflow.framework.candidate.strategy;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.common.utils.UserContext;
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

            SysUser leaderUser = sysUserMapper.selectByUsernameSimple(resolveTenantId(), dept.getLeader());
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

    /**
     * 候选人查询租户解析（评审 §4.5-P1#5）：由 ForgeTaskActorProvider 以实例行租户
     * 快照包裹注入，本方法读上下文；无租户来源时 fail-closed 返回 null（调用方
     * 返回空集走父类默认兜底，绝不猜租户）。历史形态 UserContext.get().getTenantId()
     * 在异步链路（AI 审批推进/超时自动办理/AFTER_COMMIT 抄送）为 NPE。
     */
    protected Long resolveTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("候选人查询缺少租户上下文，返回空集: strategy={}", getClass().getSimpleName());
        }
        return tenantId;
    }
}
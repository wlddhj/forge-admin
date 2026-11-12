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
                    SysUser leaderUser = sysUserMapper.selectByUsernameSimple(resolveTenantId(), dept.getLeader());
                    if (leaderUser != null) {
                        userIds.add(leaderUser.getId());
                    }
                }
            }
            log.debug("部门负责人候选人计算完成: deptIds={}, userIds={}", deptIds, userIds);
            return userIds;
        } catch (Exception e) {
            log.warn("部门负责人候选人参数解析失败: param={}", param, e);
            return Collections.emptySet();
        }
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
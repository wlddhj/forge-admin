package com.forge.modules.workflow.framework.copy;

import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 自动抄送用户解析器
 *
 * wf_process_ext.auto_copy_strategy 的取值与候选人策略码表一致
 * （10 角色/20 部门成员/21 部门负责人/22 岗位/30 用户/36 发起人/37 发起人部门负责人/60 表达式），
 * 直接复用 BpmTaskCandidateInvoker 解析，抄送与审批共享同一套组织语义。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCopyUserResolver {

    private final BpmTaskCandidateInvoker candidateInvoker;

    /**
     * 解析自动抄送用户
     *
     * @param strategy    抄送策略码（候选人策略码表）
     * @param param       策略参数（角色/部门/岗位/用户/表达式 ID）
     * @param startUserId 流程发起人ID（36/37/60 等策略需要）
     * @param variables   流程实例变量（表达式策略需要）
     * @return 抄送用户ID集合
     */
    public Set<Long> resolveUsers(Integer strategy, String param, Long startUserId, Map<String, Object> variables) {
        if (strategy == null) {
            return Collections.emptySet();
        }
        return candidateInvoker.calculateUsers(strategy, param, new AutoCopyTaskContext(startUserId, variables));
    }

    /**
     * 抄送场景的任务上下文：仅提供发起人与流程变量，任务维度字段为空
     */
    private record AutoCopyTaskContext(Long startUserId, Map<String, Object> variables)
            implements BpmTaskCandidateStrategy.TaskContext {

        @Override
        public Long getTaskId() {
            return null;
        }

        @Override
        public String getTaskKey() {
            return null;
        }

        @Override
        public String getTaskName() {
            return null;
        }

        @Override
        public Long getInstanceId() {
            return null;
        }

        @Override
        public Long getProcessId() {
            return null;
        }

        @Override
        public Long getStartUserId() {
            return startUserId;
        }

        @Override
        public Map<String, Object> getVariables() {
            return variables;
        }

        @Override
        public String getBusinessKey() {
            return null;
        }
    }
}

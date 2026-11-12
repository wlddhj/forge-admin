package com.forge.modules.demo.listener;

import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.demo.entity.BizProject;
import com.forge.modules.demo.mapper.BizProjectMapper;
import com.forge.modules.demo.service.impl.ProjectApprovalServiceImpl;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 工作流实例事件 → 项目立项状态回写（形态A事件驱动联动示例）。
 * <p>
 * 三条纪律（见 06 文档 §6.1）：
 * ① 先按 businessKey 前缀过滤，非本业务的实例直接返回；
 * ② @Async 线程无租户上下文，从事件实例快照重建并在 finally 清理；
 * ③ 终态回写幂等：绑定实例一致 + CAS（仅 APPROVING 可写），中间态不回写。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectWorkflowEventHandler {

    private final BizProjectMapper projectMapper;
    private final ReliableEventConsumer consumer;

    @Async
    @EventListener
    public void onInstanceEvent(WorkflowInstanceEvent event) {
        String businessKey = event.getBusinessKey();
        if (businessKey == null || !businessKey.startsWith(ProjectApprovalServiceImpl.BUSINESS_KEY_PREFIX)) {
            return;
        }
        Long projectId;
        try {
            projectId = Long.parseLong(businessKey.substring(ProjectApprovalServiceImpl.BUSINESS_KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("立项事件 businessKey 无法解析业务ID: {}", businessKey);
            return;
        }

        String targetStatus = mapTerminalStatus(event);
        if (targetStatus == null) {
            return; // 中间态（start/suspend/forceComplete 等）不回写业务状态
        }

        // 可靠消费：失败退避重试+终败审计（06 文档 §6.1）；租户上下文随重试单元重建
        consumer.consume("ProjectWorkflowEventHandler", event.getInstanceId(), () -> {
            TenantContextHolder.setTenantId(resolveTenantId(event.getInstance()));
            try {
                BizProject project = projectMapper.selectById(projectId);
                if (project == null) {
                    log.warn("立项事件回写落空（记录不存在）: projectId={}, type={}", projectId, event.getEventType());
                    return;
                }
                // ① 绑定实例校验：businessKey 可被伪造，须与立项记录绑定的实例一致才允许回写；
                //    processInstanceId 为 null（尚未绑定实例的旧数据）同样跳过，不误伤
                if (!Objects.equals(project.getProcessInstanceId(), event.getInstanceId())) {
                    log.warn("立项事件实例ID与绑定实例不匹配，跳过回写: projectId={}, boundInstanceId={}, eventInstanceId={}, type={}",
                            projectId, project.getProcessInstanceId(), event.getInstanceId(), event.getEventType());
                    return;
                }
                if (targetStatus.equals(project.getApproveStatus())) {
                    return; // 事件重复投递，同值幂等跳过
                }
                // ② CAS 条件更新：仅审批中（APPROVING）允许被终态回写；
                //    已终态或已被并发处理时受影响 0 行，视为已处理跳过（防二次翻转）
                LambdaUpdateWrapper<BizProject> casWrapper = new LambdaUpdateWrapper<BizProject>()
                        .eq(BizProject::getId, projectId)
                        .eq(BizProject::getApproveStatus, BizProject.STATUS_APPROVING)
                        .set(BizProject::getApproveStatus, targetStatus);
                // approveTime 仅在有终态时间时回填（与 updateById 跳过 null 字段的旧语义一致）
                LocalDateTime approveTime = toLocalDateTime(event.getInstance());
                if (approveTime != null) {
                    casWrapper.set(BizProject::getApproveTime, approveTime);
                }
                int updated = projectMapper.update(casWrapper);
                if (updated == 0) {
                    log.info("立项状态已非审批中（已终态或并发已处理），跳过回写: projectId={}, currentStatus={}, targetStatus={}, instanceId={}",
                            projectId, project.getApproveStatus(), targetStatus, event.getInstanceId());
                    return;
                }
                log.info("立项状态回写: projectId={}, {} -> {}（instanceId={}）",
                        projectId, project.getApproveStatus(), targetStatus, event.getInstanceId());
            } finally {
                TenantContextHolder.clear();
            }
        });
    }

    /**
     * 实例终态事件 → 业务状态映射（以引擎 RuntimeServiceImpl 实际触发为准，见 06 文档 §6.1）。
     * 注意 rejectComplete 事件带两种语义，需按 InstanceState 细分：
     * state=reject(2) 审批驳回结束 → REJECTED；state=terminate(5) 发起人取消（平台 cancelProcess
     * 走 runtimeService.terminate）→ CANCELED。
     */
    private String mapTerminalStatus(WorkflowInstanceEvent event) {
        return switch (event.getEventType()) {
            // end=正常走完; timeoutComplete=超时自动办结; autoComplete=自动通过
            case end, autoComplete, timeoutComplete -> BizProject.STATUS_APPROVED;
            case rejectComplete -> isTerminated(event.getInstance())
                    ? BizProject.STATUS_CANCELED
                    : BizProject.STATUS_REJECTED;
            case autoReject -> BizProject.STATUS_REJECTED;
            case revokeComplete -> BizProject.STATUS_CANCELED;
            default -> null;
        };
    }

    private boolean isTerminated(FlwHisInstance instance) {
        return instance != null && Integer.valueOf(5).equals(instance.getInstanceState());
    }

    private Long resolveTenantId(FlwHisInstance instance) {
        if (instance == null || instance.getTenantId() == null || instance.getTenantId().isBlank()) {
            return 1L;
        }
        try {
            return Long.parseLong(instance.getTenantId());
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    /**
     * 实例终态时间（引擎实体为 java.util.Date，转 LocalDateTime 落库）
     */
    private LocalDateTime toLocalDateTime(FlwHisInstance instance) {
        Date endTime = instance != null ? instance.getEndTime() : null;
        return endTime != null ? LocalDateTime.ofInstant(endTime.toInstant(), ZoneId.systemDefault()) : null;
    }
}

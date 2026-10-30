package com.forge.modules.demo.listener;

import com.aizuda.bpm.engine.entity.FlwHisInstance;
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

/**
 * 工作流实例事件 → 项目立项状态回写（形态A事件驱动联动示例）。
 * <p>
 * 三条纪律（见 06 文档 §6.1）：
 * ① 先按 businessKey 前缀过滤，非本业务的实例直接返回；
 * ② @Async 线程无租户上下文，从事件实例快照重建并在 finally 清理；
 * ③ 终态回写幂等（同值重写无害），中间态不回写。
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
                if (targetStatus.equals(project.getApproveStatus())) {
                    return; // 事件重复投递，同值幂等跳过
                }
                BizProject update = new BizProject();
                update.setId(projectId);
                update.setApproveStatus(targetStatus);
                update.setApproveTime(toLocalDateTime(event.getInstance()));
                projectMapper.updateById(update);
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

package com.forge.modules.workflow.framework.task;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.forge.modules.workflow.dto.task.TaskResponse;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.framework.title.InstanceTitleRenderer;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务响应组装器：FlwTask/FlwHisTask → TaskResponse（含批量上下文零逐行查询版本）。
 * <p>
 * 自 WfTaskServiceImpl 平移（方法体原样搬移）；同时承载
 * {@link #isHumanApprovalTask(Integer)}（催办目标/抄送锚点共用判定）。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class TaskResponseAssembler {

    private final QueryService queryService;
    private final FlowLongIdentityService identityService;

    /**
     * 是否需要人处理的审批任务（催办目标/抄送锚点共用判定）
     * 覆盖：审批(1)、条件审批(3，设计器条件审批节点独立使用时引擎按审批处理)、
     * 转办(10)、委派(11)——转办/委派后任务行 taskType 会被引擎改写但仍是待人审批；
     * 排除：发起人待重新提交(major)、抄送(cc，仅存历史表)、定时器/触发器/子流程等待、暂存
     */
    public static boolean isHumanApprovalTask(Integer taskType) {
        return TaskType.approval.eq(taskType)
                || TaskType.conditionNode.eq(taskType)
                || TaskType.transfer.eq(taskType)
                || TaskType.delegate.eq(taskType);
    }

    public TaskResponse convertTaskToResponse(FlwTask task) {
        TaskResponse response = new TaskResponse();
        response.setId(String.valueOf(task.getId()));
        response.setName(task.getTaskName());
        response.setTaskDefinitionKey(task.getTaskKey());
        response.setProcessInstanceId(String.valueOf(task.getInstanceId()));

        // 受理人以实际参与者为准（转办/并行加签不更新任务创建人字段，
        // 直接读 create_by 会显示转办前的处理人）；多参与人时回退为待认领+名单展示
        List<FlwTaskActor> actors = queryService.getTaskActorsByTaskId(task.getId());
        List<FlwTaskActor> userActors = actors.stream()
                .filter(a -> ActorType.user.eq(a.getActorType()))
                .toList();
        if (userActors.size() == 1) {
            response.setAssignee(userActors.get(0).getActorId());
            response.setAssigneeName(userActors.get(0).getActorName());
        } else {
            // 多用户参与（或签/并行加签后）或角色部门候选池：待认领 + 参与者名单
            response.setCandidate(true);
            response.setCandidateUsers(actors.stream()
                    .map(FlwTaskActor::getActorName)
                    .distinct()
                    .collect(Collectors.toList()));
            if (!userActors.isEmpty()) {
                response.setAssignee(userActors.get(0).getActorId());
                response.setAssigneeName(userActors.get(0).getActorName());
            } else if (task.getCreateId() != null) {
                response.setAssignee(task.getCreateId());
                response.setAssigneeName(task.getCreateBy());
            }
        }

        response.setCreateTime(task.getCreateTime());
        return response;
    }

    /**
     * 转换任务响应（使用批量预取上下文，无逐行查询）
     */
    public TaskResponse convertTaskToResponseWithProcess(FlwTask task, BatchContext ctx) {
        TaskResponse response = convertTaskToResponse(task);
        FlwInstance instance = ctx.instanceMap.get(task.getInstanceId());
        if (instance != null) {
            response.setProcessInstanceId(String.valueOf(instance.getId()));
            response.setUrgent(instance.getUrgent());
            response.setInstanceTitle(InstanceTitleRenderer.extractTitle(instance.getVariable()));
            FlwProcess process = ctx.processMap.get(instance.getProcessId());
            if (process != null) {
                response.setProcessDefinitionId(String.valueOf(process.getId()));
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }
            if (StrUtil.isNotBlank(instance.getCreateId())) {
                response.setStartUserId(instance.getCreateId());
                try {
                    Long startUserId = Long.parseLong(instance.getCreateId());
                    response.setStartUserName(ctx.userNameMap.getOrDefault(startUserId, instance.getCreateBy()));
                } catch (NumberFormatException e) {
                    response.setStartUserName(instance.getCreateBy());
                }
            }
            response.setStartTime(instance.getCreateTime());
            response.setProcessNo(instance.getInstanceNo());
        }
        return response;
    }

    public TaskResponse convertHisTaskToResponse(FlwHisTask hisTask) {
        // 主类 getTaskById 与列表批量转换共用

        TaskResponse response = new TaskResponse();
        response.setId(String.valueOf(hisTask.getId()));
        response.setName(hisTask.getTaskName());
        response.setTaskDefinitionKey(hisTask.getTaskKey());
        response.setProcessInstanceId(String.valueOf(hisTask.getInstanceId()));

        if (hisTask.getCreateId() != null) {
            try {
                response.setAssignee(hisTask.getCreateId());
                response.setAssigneeName(identityService.getUserName(Long.parseLong(hisTask.getCreateId())));
            } catch (NumberFormatException ignored) {}
        }

        response.setCreateTime(hisTask.getCreateTime());
        response.setEndTime(hisTask.getFinishTime());
        return response;
    }

    /**
     * 已办/抄送转换（批量上下文版：零逐行查询）
     */
    public TaskResponse convertHisTaskToResponseWithProcess(FlwHisTask hisTask, BatchContext ctx) {
        TaskResponse response = convertHisTaskToResponse(hisTask);

        FlwHisInstance hisInstance = ctx.hisInstanceMap.get(hisTask.getInstanceId());
        if (hisInstance != null) {
            response.setProcessInstanceId(String.valueOf(hisInstance.getId()));
            response.setUrgent(hisInstance.getUrgent());
            response.setInstanceTitle(InstanceTitleRenderer.extractTitle(hisInstance.getVariable()));
            FlwProcess process = ctx.processMap.get(hisInstance.getProcessId());
            if (process != null) {
                response.setProcessDefinitionId(String.valueOf(process.getId()));
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }
            if (StrUtil.isNotBlank(hisInstance.getCreateId())) {
                response.setStartUserId(hisInstance.getCreateId());
                try {
                    Long startUserId = Long.parseLong(hisInstance.getCreateId());
                    response.setStartUserName(ctx.userNameMap.getOrDefault(startUserId, hisInstance.getCreateBy()));
                } catch (NumberFormatException e) {
                    response.setStartUserName(hisInstance.getCreateBy());
                }
            }
            response.setStartTime(hisInstance.getCreateTime());
            response.setProcessNo(hisInstance.getInstanceNo());
        }

        // 审批意见（预取 Map，每任务最新一条）
        WfApprovalComment approvalComment = ctx.latestCommentByTask.get(hisTask.getId());
        if (approvalComment != null) {
            response.setActionType(approvalComment.getActionType());
            response.setCommentText(approvalComment.getCommentText());
            if (approvalComment.getCreateTime() != null) {
                response.setEndTime(Date.from(approvalComment.getCreateTime()
                        .atZone(ZoneId.systemDefault()).toInstant()));
            }
        }

        response.setWithdrawn(
                hisTask.getTaskType().equals(TaskType.withdraw.getValue())
                || hisTask.getTaskType().equals(TaskType.saveAsDraft.getValue())
                || ApprovalActionTypeEnum.WITHDRAW.getCode().equals(response.getActionType())
        );

        // 下一节点名：实例任务升序列表中找 createTime 晚于本任务 finishTime 的第一条
        if (hisTask.getFinishTime() != null) {
            List<FlwHisTask> sequence = ctx.hisTasksByInstance.getOrDefault(hisTask.getInstanceId(), Collections.emptyList());
            for (FlwHisTask candidate : sequence) {
                if (candidate.getCreateTime() != null && candidate.getCreateTime().after(hisTask.getFinishTime())) {
                    response.setNextActivityName(candidate.getTaskName());
                    break;
                }
            }
        }

        // 拿回标记：实例运行中且后继待人审批任务存在
        if (isHumanApprovalTask(hisTask.getTaskType()) && ctx.instanceMap.containsKey(hisTask.getInstanceId())) {
            List<FlwTask> successors = ctx.activeByParentTask.getOrDefault(hisTask.getId(), Collections.emptyList());
            response.setCanReclaim(successors.stream().anyMatch(this::isHumanApprovalTaskByType));
        }

        return response;
    }

    private boolean isHumanApprovalTaskByType(FlwTask task) {
        return isHumanApprovalTask(task.getTaskType());
    }
}

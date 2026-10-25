package com.forge.modules.workflow.framework.timeline;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.engine.model.ModelHelper;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审批节点时间线构建器
 *
 * 以实例级模型快照（flw_ext_instance，含加签临时节点）线性化节点序列，
 * 按节点归组挂载历史/活动任务与审批意见：
 * - 节点状态：0 未到达 / 1 进行中（有活动任务）/ 2 已完成（有历史任务）
 * - 同一节点多轮任务（驳回重审）全部保留为独立 ApprovalTask
 * - 手动抄送记录（taskKey 继承锚点节点）不混入审批任务；模型抄送节点自身的抄送任务保留
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimelineBuilder {

    /** 节点状态：未到达 */
    public static final int NODE_STATUS_NOT_REACHED = 0;
    /** 节点状态：进行中 */
    public static final int NODE_STATUS_RUNNING = 1;
    /** 节点状态：已完成 */
    public static final int NODE_STATUS_DONE = 2;

    private final RuntimeService runtimeService;
    private final ProcessService processService;
    private final QueryService queryService;
    private final WfApprovalCommentMapper approvalCommentMapper;

    public List<ApprovalDetailResponse.ApprovalNode> build(Long instanceId, Long processId,
                                                            List<FlwHisTask> hisTasks, List<FlwTask> activeTasks) {
        List<NodeModel> nodeModels = linearInstanceNodes(instanceId, processId);
        if (nodeModels.isEmpty()) {
            log.warn("审批时间线：实例模型不可用，返回空节点: instanceId={}", instanceId);
            return Collections.emptyList();
        }

        Map<String, List<FlwHisTask>> hisByKey = hisTasks.stream()
                .filter(t -> t.getTaskKey() != null)
                .collect(Collectors.groupingBy(FlwTask::getTaskKey));
        Map<String, List<FlwTask>> activeByKey = activeTasks.stream()
                .filter(t -> t.getTaskKey() != null)
                .collect(Collectors.groupingBy(FlwTask::getTaskKey));

        Map<Long, List<FlwHisTaskActor>> hisActorsByTask = queryService.getHisTaskActorsByInstanceId(instanceId)
                .orElseGet(Collections::emptyList).stream()
                .filter(a -> a.getTaskId() != null)
                .collect(Collectors.groupingBy(FlwHisTaskActor::getTaskId));
        Map<Long, List<FlwTaskActor>> activeActorsByTask = queryService.getActiveTaskActorsByInstanceId(instanceId)
                .orElseGet(Collections::emptyList).stream()
                .filter(a -> a.getTaskId() != null)
                .collect(Collectors.groupingBy(FlwTaskActor::getTaskId));

        Map<Long, WfApprovalComment> latestCommentByTask = latestCommentByTask(instanceId);

        List<ApprovalDetailResponse.ApprovalNode> nodes = new ArrayList<>();
        for (NodeModel nodeModel : nodeModels) {
            List<FlwHisTask> nodeHisTasks = hisByKey.getOrDefault(nodeModel.getNodeKey(), Collections.emptyList());
            List<FlwTask> nodeActiveTasks = activeByKey.getOrDefault(nodeModel.getNodeKey(), Collections.emptyList());

            // 手动抄送记录 taskKey 继承锚点节点：仅抄送节点(type=2)保留 cc 任务，其余节点过滤
            boolean ccNode = TaskType.cc.eq(nodeModel.getType());
            List<FlwHisTask> filteredHis = nodeHisTasks.stream()
                    .filter(t -> ccNode || !TaskType.cc.eq(t.getTaskType()))
                    .collect(Collectors.toList());

            if (filteredHis.isEmpty() && nodeActiveTasks.isEmpty()) {
                nodes.add(notReachedNode(nodeModel));
                continue;
            }
            nodes.add(buildNode(nodeModel, filteredHis, nodeActiveTasks, hisActorsByTask, activeActorsByTask, latestCommentByTask));
        }

        // 兜底：历史任务指向的节点不在模型中（异常数据）——追加避免时间线丢环节
        Set<String> modelKeys = nodeModels.stream().map(NodeModel::getNodeKey).collect(Collectors.toSet());
        hisTasks.stream()
                .filter(t -> t.getTaskKey() != null && !modelKeys.contains(t.getTaskKey()))
                .filter(t -> !TaskType.cc.eq(t.getTaskType()))
                .collect(Collectors.groupingBy(FlwTask::getTaskKey))
                .forEach((key, tasks) -> nodes.add(buildNode(null, tasks, Collections.emptyList(),
                        hisActorsByTask, activeActorsByTask, latestCommentByTask)));
        return nodes;
    }

    /**
     * 实例级模型线性化：优先 flw_ext_instance 快照，缺失时回退流程定义模型
     */
    private List<NodeModel> linearInstanceNodes(Long instanceId, Long processId) {
        ProcessModel model = null;
        try {
            model = runtimeService.getProcessModelByInstanceId(instanceId);
        } catch (Exception e) {
            log.warn("获取实例模型快照失败，回退流程定义模型: instanceId={}", instanceId, e);
        }
        if (model == null && processId != null) {
            FlwProcess process = processService.getProcessById(processId);
            if (process != null && StrUtil.isNotBlank(process.getModelContent())) {
                try {
                    model = ModelHelper.buildProcessModel(process.getModelContent());
                    if (model != null && model.getNodeConfig() != null) {
                        model.buildParentNode(model.getNodeConfig());
                    }
                } catch (Exception e) {
                    log.warn("解析流程定义模型失败: processId={}", processId, e);
                }
            }
        }
        if (model == null || model.getNodeConfig() == null) {
            return Collections.emptyList();
        }
        return ModelHelper.getRootNodeAllChildNodes(model.getNodeConfig());
    }

    private ApprovalDetailResponse.ApprovalNode notReachedNode(NodeModel nodeModel) {
        ApprovalDetailResponse.ApprovalNode node = baseNode(nodeModel);
        node.setStatus(NODE_STATUS_NOT_REACHED);
        node.setTasks(Collections.emptyList());
        node.setCandidateUsers(assigneeNames(nodeModel));
        return node;
    }

    private ApprovalDetailResponse.ApprovalNode buildNode(NodeModel nodeModel, List<FlwHisTask> hisTasks,
                                                          List<FlwTask> activeTasks,
                                                          Map<Long, List<FlwHisTaskActor>> hisActorsByTask,
                                                          Map<Long, List<FlwTaskActor>> activeActorsByTask,
                                                          Map<Long, WfApprovalComment> latestCommentByTask) {
        ApprovalDetailResponse.ApprovalNode node;
        if (nodeModel != null) {
            node = baseNode(nodeModel);
        } else {
            // 兜底分支：模型中不存在的异常数据节点，信息取自历史任务
            node = new ApprovalDetailResponse.ApprovalNode();
            hisTasks.stream().findFirst().ifPresent(first -> {
                node.setActivityId(first.getTaskKey());
                node.setActivityName(first.getTaskName());
                node.setActivityType(first.getTaskType() == null ? null : String.valueOf(first.getTaskType()));
            });
        }
        List<ApprovalDetailResponse.ApprovalTask> tasks = new ArrayList<>();

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        for (FlwHisTask hisTask : hisTasks) {
            tasks.add(hisTaskToApprovalTask(hisTask, hisActorsByTask.getOrDefault(hisTask.getId(), Collections.emptyList()),
                    latestCommentByTask.get(hisTask.getId())));
            if (hisTask.getCreateTime() != null && (startTime == null || toLocalDateTime(hisTask.getCreateTime()).isBefore(startTime))) {
                startTime = toLocalDateTime(hisTask.getCreateTime());
            }
            if (hisTask.getFinishTime() != null) {
                LocalDateTime finish = toLocalDateTime(hisTask.getFinishTime());
                if (endTime == null || finish.isAfter(endTime)) {
                    endTime = finish;
                }
            }
        }
        for (FlwTask activeTask : activeTasks) {
            tasks.add(activeTaskToApprovalTask(activeTask, activeActorsByTask.getOrDefault(activeTask.getId(), Collections.emptyList())));
            if (activeTask.getCreateTime() != null) {
                LocalDateTime create = toLocalDateTime(activeTask.getCreateTime());
                if (startTime == null || create.isBefore(startTime)) {
                    startTime = create;
                }
            }
        }

        node.setStatus(activeTasks.isEmpty() ? NODE_STATUS_DONE : NODE_STATUS_RUNNING);
        node.setStartTime(startTime);
        node.setEndTime(endTime);
        node.setTasks(tasks);
        if (!activeTasks.isEmpty()) {
            // 活动节点候选人：非用户类型参与者名（角色/部门池）+ 模型配置处理人
            List<String> candidates = activeTasks.stream()
                    .flatMap(t -> activeActorsByTask.getOrDefault(t.getId(), Collections.emptyList()).stream())
                    .filter(a -> !ActorType.user.eq(a.getActorType()) && StrUtil.isNotBlank(a.getActorName()))
                    .map(FlwTaskActor::getActorName)
                    .distinct()
                    .collect(Collectors.toList());
            if (candidates.isEmpty() && nodeModel != null) {
                candidates = assigneeNames(nodeModel);
            }
            node.setCandidateUsers(candidates);
        } else {
            node.setCandidateUsers(Collections.emptyList());
        }
        return node;
    }

    private ApprovalDetailResponse.ApprovalNode baseNode(NodeModel nodeModel) {
        ApprovalDetailResponse.ApprovalNode node = new ApprovalDetailResponse.ApprovalNode();
        node.setActivityId(nodeModel.getNodeKey());
        node.setActivityName(nodeModel.getNodeName());
        node.setActivityType(nodeModel.getType() == null ? null : String.valueOf(nodeModel.getType()));
        return node;
    }

    private ApprovalDetailResponse.ApprovalTask hisTaskToApprovalTask(FlwHisTask hisTask,
                                                                      List<FlwHisTaskActor> actors,
                                                                      WfApprovalComment comment) {
        ApprovalDetailResponse.ApprovalTask task = new ApprovalDetailResponse.ApprovalTask();
        task.setTaskId(String.valueOf(hisTask.getId()));
        task.setStatus(NODE_STATUS_DONE);
        task.setCreateTime(toLocalDateTime(hisTask.getCreateTime()));
        task.setEndTime(toLocalDateTime(hisTask.getFinishTime()));

        FlwTaskActor userActor = firstUserActor(actors);
        if (userActor != null) {
            task.setUserName(userActor.getActorName());
            try {
                task.setUserId(Long.parseLong(userActor.getActorId()));
            } catch (NumberFormatException ignored) {
            }
        } else if (StrUtil.isNotBlank(hisTask.getCreateBy())) {
            task.setUserName(hisTask.getCreateBy());
        }

        if (comment != null) {
            task.setComment(comment.getCommentText());
            task.setActionType(comment.getActionType());
            if (comment.getUserName() != null && task.getUserName() == null) {
                task.setUserName(comment.getUserName());
            }
        }
        return task;
    }

    private ApprovalDetailResponse.ApprovalTask activeTaskToApprovalTask(FlwTask activeTask, List<FlwTaskActor> actors) {
        ApprovalDetailResponse.ApprovalTask task = new ApprovalDetailResponse.ApprovalTask();
        task.setTaskId(String.valueOf(activeTask.getId()));
        task.setStatus(NODE_STATUS_RUNNING);
        task.setCreateTime(toLocalDateTime(activeTask.getCreateTime()));

        FlwTaskActor userActor = firstUserActor(actors);
        if (userActor != null) {
            task.setUserName(userActor.getActorName());
            try {
                task.setUserId(Long.parseLong(userActor.getActorId()));
            } catch (NumberFormatException ignored) {
            }
        } else {
            task.setUserName(activeTask.getCreateBy());
        }
        return task;
    }

    private FlwTaskActor firstUserActor(List<? extends FlwTaskActor> actors) {
        for (FlwTaskActor actor : actors) {
            if (ActorType.user.eq(actor.getActorType())) {
                return actor;
            }
        }
        return null;
    }

    private List<String> assigneeNames(NodeModel nodeModel) {
        if (nodeModel == null || nodeModel.getNodeAssigneeList() == null) {
            return Collections.emptyList();
        }
        return nodeModel.getNodeAssigneeList().stream()
                .map(NodeAssignee::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    /**
     * 实例内全部审批意见，按任务取最新一条
     */
    private Map<Long, WfApprovalComment> latestCommentByTask(Long instanceId) {
        LambdaQueryWrapper<WfApprovalComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfApprovalComment::getProcessInstanceId, instanceId)
                .orderByAsc(WfApprovalComment::getCreateTime);
        List<WfApprovalComment> comments = approvalCommentMapper.selectList(wrapper);
        // 升序遍历后 put，同 key 后写覆盖前写 → 保留最新
        Map<Long, WfApprovalComment> byTask = new HashMap<>();
        for (WfApprovalComment comment : comments) {
            if (comment.getTaskId() != null) {
                byTask.put(comment.getTaskId(), comment);
            }
        }
        return byTask;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}

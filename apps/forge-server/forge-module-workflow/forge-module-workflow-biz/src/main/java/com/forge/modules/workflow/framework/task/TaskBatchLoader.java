package com.forge.modules.workflow.framework.task;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务列表批量预取器：产出 {@link BatchContext}，替代转换链逐行查询（去 N+1）。
 * <p>
 * 自 WfTaskServiceImpl 平移（方法体原样搬移）：活动侧 {@link #preloadBatch} 与
 * 已办/抄送侧 {@link #preloadHisBatch}。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class TaskBatchLoader {

    private final FlwTaskMapper taskMapper;
    private final FlwHisTaskMapper hisTaskMapper;
    private final FlwInstanceMapper instanceMapper;
    private final FlwHisInstanceMapper hisInstanceMapper;
    private final ProcessService processService;
    private final FlowLongIdentityService identityService;
    private final WfApprovalCommentMapper approvalCommentMapper;

    @SuppressWarnings("unchecked")
    public BatchContext preloadBatch(List<? extends FlwTask> tasks, boolean his) {
        BatchContext ctx = new BatchContext();
        if (tasks == null || tasks.isEmpty()) return ctx;

        Set<Long> instanceIds = tasks.stream().map(FlwTask::getInstanceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> processIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();

        if (!instanceIds.isEmpty()) {
            if (his) {
                List<FlwHisInstance> instances = hisInstanceMapper.selectBatchIds(instanceIds);
                instances.forEach(i -> {
                    ctx.hisInstanceMap.put(i.getId(), i);
                    if (i.getProcessId() != null) processIds.add(i.getProcessId());
                    if (i.getCreateId() != null) {
                        try { userIds.add(Long.parseLong(i.getCreateId())); } catch (NumberFormatException ignored) {}
                    }
                });
            } else {
                List<FlwInstance> instances = instanceMapper.selectBatchIds(instanceIds);
                instances.forEach(i -> {
                    ctx.instanceMap.put(i.getId(), i);
                    if (i.getProcessId() != null) processIds.add(i.getProcessId());
                    if (i.getCreateId() != null) {
                        try { userIds.add(Long.parseLong(i.getCreateId())); } catch (NumberFormatException ignored) {}
                    }
                });
            }
        }
        if (!processIds.isEmpty()) {
            processIds.forEach(pid -> {
                FlwProcess process = processService.getProcessById(pid);
                if (process != null) ctx.processMap.put(pid, process);
            });
        }
        if (!userIds.isEmpty()) {
            ctx.userNameMap.putAll(identityService.getUserNames(userIds));
        }
        return ctx;
    }

    /**
     * 已办/抄送列表批量预取：实例/流程/发起人 + 意见(按任务取最新) + 实例任务序(算下一节点) + 拿回后继
     */
    public BatchContext preloadHisBatch(List<FlwHisTask> hisTasks) {
        BatchContext ctx = new BatchContext();
        if (hisTasks == null || hisTasks.isEmpty()) return ctx;

        // 1. 批量实例/流程/发起人（与活动侧同一套填充逻辑）
        fillInstanceProcessUser(ctx, hisTasks.stream().map(FlwHisTask::getInstanceId).collect(Collectors.toSet()));

        // 2. 实例内全部历史任务（升序）→ 下一节点名与意见任务分组共用
        Set<Long> instanceIds = ctx.hisInstanceMap.keySet();
        if (!instanceIds.isEmpty()) {
            LambdaQueryWrapper<FlwHisTask> hisWrapper = new LambdaQueryWrapper<>();
            hisWrapper.in(FlwHisTask::getInstanceId, instanceIds).orderByAsc(FlwHisTask::getCreateTime);
            hisTaskMapper.selectList(hisWrapper).forEach(t ->
                    ctx.hisTasksByInstance.computeIfAbsent(t.getInstanceId(), k -> new ArrayList<>()).add(t));
        }

        // 3. 意见：按任务 ID 分组，升序遍历后写覆盖 → 保留每任务最新一条
        if (!instanceIds.isEmpty()) {
            LambdaQueryWrapper<WfApprovalComment> commentWrapper = new LambdaQueryWrapper<>();
            commentWrapper.in(WfApprovalComment::getProcessInstanceId, instanceIds)
                    .orderByAsc(WfApprovalComment::getCreateTime);
            for (WfApprovalComment comment : approvalCommentMapper.selectList(commentWrapper)) {
                if (comment.getTaskId() != null) {
                    ctx.latestCommentByTask.put(comment.getTaskId(), comment);
                }
            }
        }

        // 4. 拿回后继：一次查全部 parentTaskId 命中的活动任务 + 运行中实例集合
        Set<Long> hisTaskIds = hisTasks.stream().map(FlwHisTask::getId).collect(Collectors.toSet());
        if (!hisTaskIds.isEmpty()) {
            LambdaQueryWrapper<FlwTask> activeWrapper = new LambdaQueryWrapper<>();
            activeWrapper.in(FlwTask::getParentTaskId, hisTaskIds);
            taskMapper.selectList(activeWrapper).forEach(t ->
                    ctx.activeByParentTask.computeIfAbsent(t.getParentTaskId(), k -> new ArrayList<>()).add(t));
            Set<Long> runningIds = hisTasks.stream().map(FlwHisTask::getInstanceId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!runningIds.isEmpty()) {
                LambdaQueryWrapper<FlwInstance> runningWrapper = new LambdaQueryWrapper<>();
                runningWrapper.in(FlwInstance::getId, runningIds);
                instanceMapper.selectList(runningWrapper).forEach(i -> ctx.instanceMap.put(i.getId(), i));
            }
        }
        return ctx;
    }

    /** 实例/流程/发起人批量填充（活动与历史侧共用） */
    private void fillInstanceProcessUser(BatchContext ctx, Set<Long> instanceIds) {
        if (instanceIds.isEmpty()) return;
        Set<Long> processIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        List<FlwHisInstance> hisInstances = hisInstanceMapper.selectBatchIds(instanceIds);
        hisInstances.forEach(i -> {
            ctx.hisInstanceMap.put(i.getId(), i);
            if (i.getProcessId() != null) processIds.add(i.getProcessId());
            if (StrUtil.isNotBlank(i.getCreateId())) {
                try { userIds.add(Long.parseLong(i.getCreateId())); } catch (NumberFormatException ignored) {}
            }
        });
        processIds.forEach(pid -> {
            FlwProcess process = processService.getProcessById(pid);
            if (process != null) ctx.processMap.put(pid, process);
        });
        if (!userIds.isEmpty()) {
            ctx.userNameMap.putAll(identityService.getUserNames(userIds));
        }
    }
}

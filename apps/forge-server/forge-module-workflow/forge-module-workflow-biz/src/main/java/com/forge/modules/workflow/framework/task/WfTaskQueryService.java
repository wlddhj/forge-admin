package com.forge.modules.workflow.framework.task;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.AgentType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwHisTaskActor;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwProcessMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.dto.task.TaskQueryRequest;
import com.forge.modules.workflow.dto.task.TaskResponse;
import com.forge.modules.workflow.framework.WorkflowSupport;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 任务四列表查询服务（待办/可认领/已办/抄送 + 实例抄送清单）。
 * <p>
 * 自 WfTaskServiceImpl 平移：四列表共享"actor 圈任务 → 两步式流程名/标题过滤（圈实例再 in 任务）
 * → 分页 → 批量预取 → 转换/装饰"骨架，收敛为模板方法 {@link #queryTaskPage(TaskListQuery)}，
 * actor 谓词/额外任务谓词/decorator 函数化。
 * <p>
 * 行为修复（本批唯一授权例外）：getClaimableTasks 原先静默丢弃 title/processDefinitionName
 * 入参，现按待办分支的两段过滤实现原样补齐。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class WfTaskQueryService {

    private final FlwTaskMapper taskMapper;
    private final FlwHisTaskMapper hisTaskMapper;
    private final FlwTaskActorMapper taskActorMapper;
    private final FlwHisTaskActorMapper hisTaskActorMapper;
    private final FlwInstanceMapper instanceMapper;
    private final FlwHisInstanceMapper hisInstanceMapper;
    private final FlwProcessMapper processMapper;
    private final QueryService queryService;
    private final FlowLongIdentityService identityService;
    private final TaskBatchLoader batchLoader;
    private final TaskResponseAssembler assembler;

    public Page<TaskResponse> getTodoTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userIdStr = String.valueOf(currentUserId);

        // 查询当前用户作为参与者的待办任务
        // 1. 先查询 flw_task_actor 表，找到当前用户参与的 taskId
        List<FlwTaskActor> taskActors = taskActorMapper.selectList(
                new LambdaQueryWrapper<FlwTaskActor>().eq(FlwTaskActor::getActorId, userIdStr));

        // 本人从候选组（角色/部门）签收的任务标记，供前端展示“取消签收”
        Set<Long> claimedFromGroupTaskIds = taskActors.stream()
                .filter(a -> AgentType.claimRole.eq(a.getAgentType()) || AgentType.claimDepartment.eq(a.getAgentType()))
                .map(FlwTaskActor::getTaskId)
                .collect(Collectors.toSet());

        return queryTaskPage(new TaskListQuery<>(
                request,
                () -> taskActors.stream().map(FlwTaskActor::getTaskId).toList(),
                (wrapper, ids) -> wrapper.in(FlwTask::getId, ids),
                FlwTask::getTaskName,
                FlwTask::getInstanceId,
                FlwTask::getCreateTime,
                false,
                true,
                true,
                taskMapper::selectPage,
                tasks -> batchLoader.preloadBatch(tasks, false),
                assembler::convertTaskToResponseWithProcess,
                (task, response) -> response.setClaimedFromGroup(claimedFromGroupTaskIds.contains(task.getId()))));
    }

    public Page<TaskResponse> getClaimableTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        // 用户经角色/部门候选池可见的任务：actor 记录的角色/部门 ID ∈ 用户隶属集
        // 角色直接取 JWT 过滤器装载的 UserContext（免 DB 查询）；部门另查用户主表
        Set<String> groupIds = new HashSet<>();
        UserContext context = UserContext.get();
        if (context != null && context.getRoles() != null) {
            context.getRoles().forEach(role -> {
                if (role.getRoleId() != null) {
                    groupIds.add(String.valueOf(role.getRoleId()));
                }
            });
        }
        Long deptId = identityService.getUserDeptId(currentUserId);
        if (deptId != null) {
            groupIds.add(String.valueOf(deptId));
        }
        if (groupIds.isEmpty()) {
            return emptyTaskPage(request);
        }

        LambdaQueryWrapper<FlwTaskActor> actorWrapper = new LambdaQueryWrapper<>();
        actorWrapper.in(FlwTaskActor::getActorId, groupIds);
        actorWrapper.in(FlwTaskActor::getActorType, List.of(ActorType.role.getValue(), ActorType.department.getValue()));
        List<FlwTaskActor> groupActors = taskActorMapper.selectList(actorWrapper);
        if (groupActors.isEmpty()) {
            return emptyTaskPage(request);
        }

        Map<Long, List<FlwTaskActor>> actorsByTask = groupActors.stream()
                .collect(Collectors.groupingBy(FlwTaskActor::getTaskId));

        return queryTaskPage(new TaskListQuery<>(
                request,
                () -> List.copyOf(actorsByTask.keySet()),
                (wrapper, ids) -> wrapper.in(FlwTask::getId, ids),
                FlwTask::getTaskName,
                FlwTask::getInstanceId,
                FlwTask::getCreateTime,
                false,
                true,
                true,
                taskMapper::selectPage,
                tasks -> batchLoader.preloadBatch(tasks, false),
                assembler::convertTaskToResponseWithProcess,
                (task, response) -> {
                    response.setCandidate(true);
                    response.setCandidateUsers(actorsByTask.getOrDefault(task.getId(), Collections.emptyList()).stream()
                            .map(FlwTaskActor::getActorName)
                            .distinct()
                            .collect(Collectors.toList()));
                }));
    }

    public Page<TaskResponse> getDoneTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userIdStr = String.valueOf(currentUserId);

        // 查询当前用户作为参与者的历史任务
        // 1. 先查询 flw_his_task_actor 表，找到当前用户参与的历史 taskId
        List<FlwHisTaskActor> hisTaskActors = hisTaskActorMapper.selectList(
                new LambdaQueryWrapper<FlwHisTaskActor>().eq(FlwHisTaskActor::getActorId, userIdStr));

        return queryTaskPage(new TaskListQuery<>(
                request,
                () -> hisTaskActors.stream().map(FlwHisTaskActor::getTaskId).toList(),
                (wrapper, ids) -> {
                    wrapper.in(FlwHisTask::getId, ids);
                    // 只查询已完成的任务（task_state > 0 表示已完成）
                    wrapper.gt(FlwHisTask::getTaskState, 0);
                    // 排除发起节点的发起人任务（taskType=0），已办只展示真实审批记录
                    wrapper.ne(FlwHisTask::getTaskType, 0);
                },
                FlwHisTask::getTaskName,
                FlwHisTask::getInstanceId,
                FlwHisTask::getFinishTime,
                true,
                true,
                true,
                hisTaskMapper::selectPage,
                batchLoader::preloadHisBatch,
                assembler::convertHisTaskToResponseWithProcess,
                null));
    }

    public Page<TaskResponse> getCcTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userIdStr = String.valueOf(currentUserId);

        // 查询当前用户作为抄送人的历史任务参与者记录
        // 抄送人通过 weight = 6 来标识
        List<FlwHisTaskActor> ccTaskActors = hisTaskActorMapper.selectList(
                new LambdaQueryWrapper<FlwHisTaskActor>()
                        .eq(FlwHisTaskActor::getActorId, userIdStr)
                        .eq(FlwHisTaskActor::getWeight, 6)); // 抄送人标识

        return queryTaskPage(new TaskListQuery<>(
                request,
                () -> ccTaskActors.stream().map(FlwHisTaskActor::getTaskId).toList(),
                (wrapper, ids) -> {
                    wrapper.in(FlwHisTask::getId, ids);
                    wrapper.eq(FlwHisTask::getTaskType, TaskType.cc.getValue()); // 抄送任务类型
                },
                FlwHisTask::getTaskName,
                FlwHisTask::getInstanceId,
                FlwHisTask::getCreateTime,
                false,
                false,
                false,
                hisTaskMapper::selectPage,
                batchLoader::preloadHisBatch,
                assembler::convertHisTaskToResponseWithProcess,
                null));
    }

    public List<TaskResponse> getCcTasksByInstanceId(Long instanceId) {
        // 使用 FlowLong 的查询服务获取流程实例的抄送任务参与者
        Optional<List<FlwHisTaskActor>> ccTaskActorsOpt = queryService.getCcTaskActorsByInstanceId(instanceId);

        if (ccTaskActorsOpt.isEmpty() || ccTaskActorsOpt.get().isEmpty()) {
            return Collections.emptyList();
        }

        List<FlwHisTaskActor> ccTaskActors = ccTaskActorsOpt.get();

        // 获取所有抄送 taskId
        Set<Long> ccTaskIds = ccTaskActors.stream()
                .map(FlwHisTaskActor::getTaskId)
                .collect(Collectors.toSet());

        // 查询历史抄送任务
        LambdaQueryWrapper<FlwHisTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(FlwHisTask::getId, ccTaskIds);
        taskWrapper.eq(FlwHisTask::getTaskType, TaskType.cc.getValue());
        taskWrapper.orderByDesc(FlwHisTask::getCreateTime);

        List<FlwHisTask> ccTasks = hisTaskMapper.selectList(taskWrapper);

        // 转换为响应对象
        BatchContext ccCtx = batchLoader.preloadHisBatch(ccTasks);
        return ccTasks.stream()
                .map(t -> assembler.convertHisTaskToResponseWithProcess(t, ccCtx))
                .collect(Collectors.toList());
    }

    /**
     * 四列表模板方法：actor 圈任务 → 任务查询（名称/流程名/标题过滤 + 额外谓词）→ 分页
     * → 批量预取 → 转换/装饰。
     * <p>
     * 流程名与标题均为两步式过滤：先按条件圈实例 ID（includeHis 时活动表+历史表并集，
     * 已办任务两态并存），再 in 任务；无命中所见即所得快速空页。
     */
    private <T> Page<TaskResponse> queryTaskPage(TaskListQuery<T> query) {
        TaskQueryRequest request = query.request();
        List<Long> taskIds = query.taskIdsLoader().get();
        if (taskIds.isEmpty()) {
            return emptyTaskPage(request);
        }

        LambdaQueryWrapper<T> taskWrapper = new LambdaQueryWrapper<>();
        query.baseWrapperCustomizer().accept(taskWrapper, taskIds);

        // 任务名称筛选
        if (StrUtil.isNotBlank(request.getName())) {
            taskWrapper.like(query.nameColumn(), request.getName());
        }

        // 流程名称过滤两步式：圈流程→实例，再 in 任务
        if (query.includeProcessNameFilter() && StrUtil.isNotBlank(request.getProcessDefinitionName())) {
            List<Long> nameInstanceIds = instanceIdsByProcessName(
                    request.getProcessDefinitionName(), query.includeHis());
            if (nameInstanceIds.isEmpty()) {
                return emptyTaskPage(request);
            }
            taskWrapper.in(query.instanceIdColumn(), nameInstanceIds);
        }

        // 标题过滤两步式：先按 __title 圈实例，再 in 任务（顶层谓词避 EXISTS 方言差异）
        if (query.includeTitleFilter()) {
            String titleKeyword = WorkflowSupport.escapeLike(request.getTitle());
            if (titleKeyword != null) {
                List<Long> activeTitleIds = instanceMapper.selectList(new LambdaQueryWrapper<FlwInstance>()
                                .select(FlwInstance::getId)
                                .apply("variable LIKE CONCAT('%\"__title\":\"%', {0}, '%') ESCAPE '|'", titleKeyword))
                        .stream().map(FlwInstance::getId).toList();
                if (query.includeHis()) {
                    // 流程可能仍在运行（实例在活动表）或已结束（在历史表），已办任务两态并存，故两侧并集
                    Set<Long> titleInstanceIds = new HashSet<>(activeTitleIds);
                    titleInstanceIds.addAll(hisInstanceMapper.selectList(
                                    new LambdaQueryWrapper<FlwHisInstance>()
                                            .select(FlwHisInstance::getId)
                                            .apply("variable LIKE CONCAT('%\"__title\":\"%', {0}, '%') ESCAPE '|'", titleKeyword))
                            .stream().map(FlwHisInstance::getId).toList());
                    if (titleInstanceIds.isEmpty()) {
                        return emptyTaskPage(request);
                    }
                    taskWrapper.in(query.instanceIdColumn(), titleInstanceIds);
                } else {
                    if (activeTitleIds.isEmpty()) {
                        return emptyTaskPage(request);
                    }
                    taskWrapper.in(query.instanceIdColumn(), activeTitleIds);
                }
            }
        }

        taskWrapper.orderByDesc(query.orderColumn());

        Page<T> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<T> taskPage = query.pageExecutor().apply(pageParam, taskWrapper);

        // 4. 批量预取实例/流程/发起人（去 N+1：每页 3 次批量替代逐行 3N 次）
        BatchContext ctx = query.batchLoader().apply(taskPage.getRecords());
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(task -> {
                    TaskResponse response = query.converter().apply(task, ctx);
                    if (query.decorator() != null) {
                        query.decorator().accept(task, response);
                    }
                    return response;
                })
                .collect(Collectors.toList());

        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 按流程名称模糊圈实例 ID：先圈流程再圈实例（includeHis=已办场景实例可能在途或已结束，两侧并集）
     */
    private List<Long> instanceIdsByProcessName(String processName, boolean includeHis) {
        Set<Long> processIds = processMapper.selectList(new LambdaQueryWrapper<FlwProcess>()
                        .select(FlwProcess::getId)
                        .like(FlwProcess::getProcessName, processName))
                .stream().map(FlwProcess::getId)
                .collect(Collectors.toSet());
        if (processIds.isEmpty()) {
            return List.of();
        }
        Set<Long> instanceIds = new HashSet<>(instanceMapper.selectList(
                        new LambdaQueryWrapper<FlwInstance>()
                                .select(FlwInstance::getId)
                                .in(FlwInstance::getProcessId, processIds))
                .stream().map(FlwInstance::getId).toList());
        if (includeHis) {
            instanceIds.addAll(hisInstanceMapper.selectList(
                            new LambdaQueryWrapper<FlwHisInstance>()
                                    .select(FlwHisInstance::getId)
                                    .in(FlwHisInstance::getProcessId, processIds))
                    .stream().map(FlwHisInstance::getId).toList());
        }
        return List.copyOf(instanceIds);
    }

    /**
     * 标题/模糊关键字无命中的空页快速返回
     */
    private Page<TaskResponse> emptyTaskPage(TaskQueryRequest request) {
        Page<TaskResponse> empty = new Page<>(request.getPageNum(), request.getPageSize());
        empty.setTotal(0);
        empty.setRecords(Collections.emptyList());
        return empty;
    }

    /**
     * 四列表查询参数化载体：actor 圈任务谓词/额外任务谓词/decorator/includeHis/过滤开关函数化。
     * decorator 可为 null（已办/抄送无后置装饰）。
     */
    private record TaskListQuery<T>(
            TaskQueryRequest request,
            Supplier<List<Long>> taskIdsLoader,
            BiConsumer<LambdaQueryWrapper<T>, List<Long>> baseWrapperCustomizer,
            SFunction<T, ?> nameColumn,
            SFunction<T, ?> instanceIdColumn,
            SFunction<T, ?> orderColumn,
            boolean includeHis,
            boolean includeProcessNameFilter,
            boolean includeTitleFilter,
            BiFunction<Page<T>, LambdaQueryWrapper<T>, Page<T>> pageExecutor,
            Function<List<T>, BatchContext> batchLoader,
            BiFunction<T, BatchContext, TaskResponse> converter,
            BiConsumer<T, TaskResponse> decorator) {
    }
}

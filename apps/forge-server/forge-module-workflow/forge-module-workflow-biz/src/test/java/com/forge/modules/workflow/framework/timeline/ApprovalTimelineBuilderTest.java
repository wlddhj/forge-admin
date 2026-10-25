package com.forge.modules.workflow.framework.timeline;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.core.FlowLongContext;
import com.aizuda.bpm.spring.adaptive.FlowJacksonHandler;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwHisTaskActor;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.forge.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 审批节点时间线构建器单元测试
 *
 * 覆盖：多轮驳回任务归组、并行分支未到达、手动抄送过滤、模型快照缺失回退
 */
class ApprovalTimelineBuilderTest {

    private static final Long INSTANCE_ID = 100L;
    private static final Long PROCESS_ID = 10L;

    private RuntimeService runtimeService;
    private ProcessService processService;
    private QueryService queryService;
    private WfApprovalCommentMapper approvalCommentMapper;
    private ApprovalTimelineBuilder builder;

    @BeforeEach
    void setUp() {
        // ModelHelper 静态解析依赖引擎上下文的 JSON handler，单测环境手动装配
        FlowLongContext.setFlowJsonHandler(new FlowJacksonHandler());
        runtimeService = mock(RuntimeService.class);
        processService = mock(ProcessService.class);
        queryService = mock(QueryService.class);
        approvalCommentMapper = mock(WfApprovalCommentMapper.class);
        builder = new ApprovalTimelineBuilder(runtimeService, processService, queryService, approvalCommentMapper);

        when(queryService.getHisTaskActorsByInstanceId(INSTANCE_ID)).thenReturn(Optional.of(new ArrayList<>()));
        when(queryService.getActiveTaskActorsByInstanceId(INSTANCE_ID)).thenReturn(Optional.of(new ArrayList<>()));
        when(approvalCommentMapper.selectList(any())).thenReturn(new ArrayList<>());
    }

    private NodeModel node(String key, String name, int type) {
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey(key);
        nodeModel.setNodeName(name);
        nodeModel.setType(type);
        return nodeModel;
    }

    /** 发起人 → 审批1 → 条件分支(分支A/分支B) 模型 */
    private ProcessModel branchModel() {
        NodeModel start = node("start_1", "发起人", 0);
        NodeModel approval1 = node("node_1", "一级审批", 1);
        NodeModel branch = node("branch_1", "条件分支", 4);
        NodeModel branchA = node("node_a", "A分支审批", 1);
        NodeModel branchB = node("node_b", "B分支审批", 1);

        NodeAssignee assignee = new NodeAssignee();
        assignee.setId("5");
        assignee.setName("李四");
        branchB.setNodeAssigneeList(List.of(assignee));

        ConditionNode condA = new ConditionNode();
        condA.setNodeKey("cond_a");
        condA.setType(3);
        condA.setChildNode(branchA);
        ConditionNode condB = new ConditionNode();
        condB.setNodeKey("cond_b");
        condB.setType(3);
        condB.setChildNode(branchB);
        branch.setConditionNodes(new ArrayList<>(List.of(condA, condB)));

        start.setChildNode(approval1);
        approval1.setChildNode(branch);
        ProcessModel model = new ProcessModel();
        model.setNodeConfig(start);
        return model;
    }

    private FlwHisTask hisTask(long id, String key, String name, int taskType, Date create, Date finish) {
        FlwHisTask hisTask = new FlwHisTask();
        hisTask.setId(id);
        hisTask.setInstanceId(INSTANCE_ID);
        hisTask.setTaskKey(key);
        hisTask.setTaskName(name);
        hisTask.setTaskType(taskType);
        hisTask.setCreateTime(create);
        hisTask.setFinishTime(finish);
        return hisTask;
    }

    @Test
    @DisplayName("多轮驳回节点应归组全部轮次任务并挂载意见")
    void build_shouldKeepAllRoundsWithComments() {
        when(runtimeService.getProcessModelByInstanceId(INSTANCE_ID)).thenReturn(branchModel());

        Date t1 = new Date(System.currentTimeMillis() - 60000);
        Date t2 = new Date(System.currentTimeMillis() - 30000);
        FlwHisTask round1 = hisTask(11L, "node_1", "一级审批", 1, t1, t1);
        FlwHisTask round2 = hisTask(12L, "node_1", "一级审批", 1, t2, t2);
        FlwHisTask start = hisTask(10L, "start_1", "发起人", 0, new Date(System.currentTimeMillis() - 90000), null);

        FlwHisTaskActor actor = new FlwHisTaskActor();
        actor.setTaskId(11L);
        actor.setActorId("2");
        actor.setActorName("张三");
        actor.setActorType(0);
        when(queryService.getHisTaskActorsByInstanceId(INSTANCE_ID)).thenReturn(Optional.of(new ArrayList<>(List.of(actor))));

        WfApprovalComment comment = new WfApprovalComment();
        comment.setTaskId(11L);
        comment.setTaskDefKey("node_1");
        comment.setActionType("reject");
        comment.setCommentText("金额有误");
        comment.setCreateTime(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));
        when(approvalCommentMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(comment)));

        List<ApprovalDetailResponse.ApprovalNode> nodes = builder.build(INSTANCE_ID, PROCESS_ID,
                List.of(start, round1, round2), Collections.emptyList());

        assertEquals(4, nodes.size(), "发起人+审批1+A分支+B分支");
        ApprovalDetailResponse.ApprovalNode approvalNode = nodes.stream()
                .filter(n -> "node_1".equals(n.getActivityId())).findFirst().orElseThrow();
        assertEquals(ApprovalTimelineBuilder.NODE_STATUS_DONE, approvalNode.getStatus());
        assertEquals(2, approvalNode.getTasks().size(), "驳回重审两轮任务均保留");

        ApprovalDetailResponse.ApprovalTask first = approvalNode.getTasks().get(0);
        assertEquals("张三", first.getUserName());
        assertEquals("reject", first.getActionType());
        assertEquals("金额有误", first.getComment());
    }

    @Test
    @DisplayName("未走到的分支节点应为未到达状态并展示模型处理人")
    void build_shouldMarkUnvisitedBranchNotReached() {
        when(runtimeService.getProcessModelByInstanceId(INSTANCE_ID)).thenReturn(branchModel());

        FlwHisTask start = hisTask(10L, "start_1", "发起人", 0, new Date(), null);
        FlwHisTask approval1 = hisTask(11L, "node_1", "一级审批", 1, new Date(), new Date());
        FlwHisTask branchA = hisTask(12L, "node_a", "A分支审批", 1, new Date(), new Date());

        List<ApprovalDetailResponse.ApprovalNode> nodes = builder.build(INSTANCE_ID, PROCESS_ID,
                List.of(start, approval1, branchA), Collections.emptyList());

        ApprovalDetailResponse.ApprovalNode nodeB = nodes.stream()
                .filter(n -> "node_b".equals(n.getActivityId())).findFirst().orElseThrow();
        assertEquals(ApprovalTimelineBuilder.NODE_STATUS_NOT_REACHED, nodeB.getStatus());
        assertTrue(nodeB.getTasks().isEmpty());
        assertEquals(List.of("李四"), nodeB.getCandidateUsers());

        ApprovalDetailResponse.ApprovalNode nodeA = nodes.stream()
                .filter(n -> "node_a".equals(n.getActivityId())).findFirst().orElseThrow();
        assertEquals(ApprovalTimelineBuilder.NODE_STATUS_DONE, nodeA.getStatus());
    }

    @Test
    @DisplayName("活动任务节点为进行中且手动抄送记录不混入审批任务")
    void build_shouldFilterManualCcAndMarkRunning() {
        when(runtimeService.getProcessModelByInstanceId(INSTANCE_ID)).thenReturn(branchModel());

        FlwHisTask start = hisTask(10L, "start_1", "发起人", 0, new Date(), null);
        // 手动抄送：taskType=cc 但 taskKey 继承审批节点
        FlwHisTask manualCc = hisTask(13L, "node_a", "一级审批", 2, new Date(), new Date());
        FlwTask active = new FlwTask();
        active.setId(14L);
        active.setInstanceId(INSTANCE_ID);
        active.setTaskKey("node_a");
        active.setTaskName("A分支审批");
        active.setTaskType(1);
        active.setCreateTime(new Date());

        FlwTaskActor activeActor = new FlwTaskActor();
        activeActor.setTaskId(14L);
        activeActor.setActorId("3");
        activeActor.setActorName("王五");
        activeActor.setActorType(0);
        when(queryService.getActiveTaskActorsByInstanceId(INSTANCE_ID)).thenReturn(Optional.of(new ArrayList<>(List.of(activeActor))));

        List<ApprovalDetailResponse.ApprovalNode> nodes = builder.build(INSTANCE_ID, PROCESS_ID,
                List.of(start, manualCc), List.of(active));

        ApprovalDetailResponse.ApprovalNode nodeA = nodes.stream()
                .filter(n -> "node_a".equals(n.getActivityId())).findFirst().orElseThrow();
        assertEquals(ApprovalTimelineBuilder.NODE_STATUS_RUNNING, nodeA.getStatus());
        assertEquals(1, nodeA.getTasks().size(), "手动抄送记录被过滤，仅剩活动审批任务");
        assertEquals("王五", nodeA.getTasks().get(0).getUserName());
        assertEquals(ApprovalTimelineBuilder.NODE_STATUS_RUNNING, nodeA.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("实例模型快照缺失时回退流程定义模型")
    void build_shouldFallbackToProcessModel() {
        when(runtimeService.getProcessModelByInstanceId(INSTANCE_ID)).thenThrow(new RuntimeException("ext instance missing"));
        com.aizuda.bpm.engine.entity.FlwProcess process = new com.aizuda.bpm.engine.entity.FlwProcess();
        process.setId(PROCESS_ID);
        process.setModelContent("{\"name\":\"test\",\"nodeConfig\":{\"nodeName\":\"发起人\",\"nodeKey\":\"start_1\",\"type\":0}}");
        when(processService.getProcessById(PROCESS_ID)).thenReturn(process);

        FlwHisTask start = hisTask(10L, "start_1", "发起人", 0, new Date(), null);

        List<ApprovalDetailResponse.ApprovalNode> nodes = builder.build(INSTANCE_ID, PROCESS_ID,
                List.of(start), Collections.emptyList());

        assertEquals(1, nodes.size());
        assertEquals("start_1", nodes.get(0).getActivityId());
        assertEquals(ApprovalTimelineBuilder.NODE_STATUS_DONE, nodes.get(0).getStatus());
    }
}

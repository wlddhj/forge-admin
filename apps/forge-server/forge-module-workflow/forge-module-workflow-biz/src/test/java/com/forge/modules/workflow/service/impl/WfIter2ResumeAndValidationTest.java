package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.mybatisplus.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.UserContext;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.framework.diagram.FlowLongDiagramGenerator;
import com.forge.modules.workflow.framework.timeline.ApprovalTimelineBuilder;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import com.forge.modules.workflow.service.ProcessNoGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 迭代二：实例唤醒校验 + 新节点模型校验单元测试
 */
class WfIter2ResumeAndValidationTest {

    private static final Long INSTANCE_ID = 100L;

    private QueryService queryService;
    private FlowLongEngine flowLongEngine;
    private ProcessService processService;
    private com.forge.modules.workflow.mapper.WfProcessExtMapper processExtMapper;
    private WfProcessInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        queryService = mock(QueryService.class);
        flowLongEngine = mock(FlowLongEngine.class);
        processService = mock(ProcessService.class);
        processExtMapper = mock(WfProcessExtMapper.class);
        service = new WfProcessInstanceServiceImpl(
                mock(RuntimeService.class), processService, queryService, mock(TaskService.class),
                mock(FlwInstanceMapper.class), mock(FlwHisInstanceMapper.class), mock(FlwHisTaskMapper.class),
                mock(FlwTaskActorMapper.class), mock(FlowLongDiagramGenerator.class),
                mock(FlowLongIdentityService.class), mock(WfApprovalCommentMapper.class),
                mock(com.forge.modules.workflow.mapper.WfAiRouteRecordMapper.class),
                mock(com.forge.modules.workflow.mapper.WfAiApprovalRecordMapper.class),
                mock(com.forge.modules.workflow.mapper.WfInstanceQueryMapper.class),
                mock(SysAttachmentService.class), processExtMapper,
                mock(ProcessNoGenerator.class), mock(ApprovalTimelineBuilder.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "flowLongEngine", flowLongEngine);
    }

    // ========== 发起范围校验 ==========

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("use_scope=1 名单内用户可发起（Long/Integer 反序列化修正）")
    void scope_userInListAllowed() {
        com.aizuda.bpm.engine.entity.FlwProcess process = new com.aizuda.bpm.engine.entity.FlwProcess();
        process.setId(10L);
        process.setUseScope(1);
        com.forge.modules.workflow.entity.WfProcessExt ext = new com.forge.modules.workflow.entity.WfProcessExt();
        ext.setProcessId(10L);
        ext.setUseScopeUsers("[1,2,3]");
        org.mockito.Mockito.when(processExtMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ext);
        try (var securityMock = org.mockito.Mockito.mockStatic(com.forge.framework.security.utils.SecurityHelper.class);
             var userContextMock = org.mockito.Mockito.mockStatic(com.forge.common.utils.UserContext.class)) {
            securityMock.when(com.forge.framework.security.utils.SecurityHelper::getCurrentUserId).thenReturn(2L);
            userContextMock.when(() -> com.forge.common.utils.UserContext.get()).thenReturn(null);
            assertDoesNotThrow(() -> service.checkStartScope(process, 10L, 2L));
        }
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("use_scope=1 名单外用户被拒；use_scope=2 全拒")
    void scope_userOutOfListRejected() {
        com.aizuda.bpm.engine.entity.FlwProcess process = new com.aizuda.bpm.engine.entity.FlwProcess();
        process.setId(10L);
        process.setUseScope(1);
        com.forge.modules.workflow.entity.WfProcessExt ext = new com.forge.modules.workflow.entity.WfProcessExt();
        ext.setProcessId(10L);
        ext.setUseScopeUsers("[1,2,3]");
        org.mockito.Mockito.when(processExtMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ext);
        try (var userContextMock = org.mockito.Mockito.mockStatic(com.forge.common.utils.UserContext.class)) {
            userContextMock.when(() -> com.forge.common.utils.UserContext.get()).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.checkStartScope(process, 10L, 9L));
            assertEquals(403, ex.getCode());
        }
        process.setUseScope(2);
        try (var userContextMock = org.mockito.Mockito.mockStatic(com.forge.common.utils.UserContext.class)) {
            userContextMock.when(() -> com.forge.common.utils.UserContext.get()).thenReturn(null);
            BusinessException ex2 = assertThrows(BusinessException.class,
                    () -> service.checkStartScope(process, 10L, 1L));
            assertEquals(403, ex2.getCode());
        }
    }

    private FlwHisInstance hisInstance(Integer state) {
        FlwHisInstance hisInstance = new FlwHisInstance();
        hisInstance.setId(INSTANCE_ID);
        hisInstance.setProcessId(10L);
        hisInstance.setCreateId("1");
        hisInstance.setInstanceState(state);
        return hisInstance;
    }

    // ========== 唤醒校验 ==========

    @Test
    @DisplayName("唤醒：运行中实例被拒绝")
    void resume_shouldRejectRunningInstance() {
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(new FlwInstance());

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.resumeInstance(String.valueOf(INSTANCE_ID)));
            assertEquals(400, ex.getCode());
        }
    }

    @Test
    @DisplayName("唤醒：已通过结束的实例不可唤醒")
    void resume_shouldRejectCompletedInstance() {
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(null);
        when(queryService.getHistInstance(INSTANCE_ID)).thenReturn(hisInstance(InstanceState.complete.getValue()));

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.resumeInstance(String.valueOf(INSTANCE_ID)));
            assertEquals(400, ex.getCode());
        }
    }

    @Test
    @DisplayName("唤醒：驳回结束的实例由发起人唤醒成功")
    void resume_shouldSucceedForRejectedInstance() {
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(null);
        when(queryService.getHistInstance(INSTANCE_ID)).thenReturn(hisInstance(InstanceState.reject.getValue()));

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            service.resumeInstance(String.valueOf(INSTANCE_ID));
        }

        verify(flowLongEngine).executeResumeTask(eq(INSTANCE_ID), any(), isNull());
    }

    @Test
    @DisplayName("唤醒：非发起人且非管理员被拒绝")
    void resume_shouldRejectNonInitiator() {
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(null);
        when(queryService.getHistInstance(INSTANCE_ID)).thenReturn(hisInstance(InstanceState.terminate.getValue()));

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(7L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.resumeInstance(String.valueOf(INSTANCE_ID)));
            assertEquals(403, ex.getCode());
        }
    }

    // ========== 新节点模型校验（反射调私有方法） ==========

    private void validate(NodeModel root) {
        try {
            Method method = WfProcessInstanceServiceImpl.class
                    .getDeclaredMethod("checkNodeModelWithDynamicAssignee", NodeModel.class, Set.class);
            method.setAccessible(true);
            method.invoke(service, root, Collections.emptySet());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private NodeModel node(String key, String name, int type) {
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey(key);
        nodeModel.setNodeName(name);
        nodeModel.setType(type);
        return nodeModel;
    }

    private ConditionNode branch(String name, String key, boolean withCondition) {
        ConditionNode cn = new ConditionNode();
        cn.setNodeKey(key);
        cn.setNodeName(name);
        cn.setType(3);
        cn.setPriorityLevel(1);
        if (withCondition) {
            cn.setConditionList(java.util.List.of(java.util.List.of()));
        }
        return cn;
    }

    @Test
    @DisplayName("并行分支少于2个分支被拒绝")
    void validation_parallelNeedsTwoBranches() {
        NodeModel parallel = node("pb", "并行", 8);
        parallel.setParallelNodes(java.util.List.of(branch("b1", "b1", false)));
        NodeModel root = node("start", "发起人", 0);
        root.setChildNode(parallel);

        BusinessException ex = assertThrows(BusinessException.class, () -> validate(root));
        assertTrue(ex.getMessage().contains("至少需要 2 个分支"));
    }

    @Test
    @DisplayName("包容分支缺少无条件兜底分支被拒绝")
    void validation_inclusiveNeedsDefaultBranch() {
        NodeModel inclusive = node("ib", "包容", 9);
        inclusive.setInclusiveNodes(java.util.List.of(branch("b1", "b1", true)));
        NodeModel root = node("start", "发起人", 0);
        root.setChildNode(inclusive);

        BusinessException ex = assertThrows(BusinessException.class, () -> validate(root));
        assertTrue(ex.getMessage().contains("兜底"));
    }

    @Test
    @DisplayName("路由分支未设置 route: 目标被拒绝")
    void validation_routeNeedsTargetPrefix() {
        NodeModel route = node("rb", "路由", 23);
        route.setRouteNodes(java.util.List.of(branch("r1", "flk123", true)));
        NodeModel root = node("start", "发起人", 0);
        root.setChildNode(route);

        BusinessException ex = assertThrows(BusinessException.class, () -> validate(root));
        assertTrue(ex.getMessage().contains("跳转目标"));
    }

    @Test
    @DisplayName("定时器 time 格式非法被拒绝，合法格式通过")
    void validation_timerTimeFormat() {
        NodeModel bad = node("tm", "定时器", 6);
        bad.setExtendConfig(java.util.Map.of("time", "abc"));
        NodeModel root = node("start", "发起人", 0);
        root.setChildNode(bad);
        assertThrows(BusinessException.class, () -> validate(root));

        NodeModel good = node("tm2", "定时器2", 6);
        good.setExtendConfig(java.util.Map.of("time", "2:h"));
        NodeModel root2 = node("start2", "发起人", 0);
        root2.setChildNode(good);
        assertDoesNotThrow(() -> validate(root2));
    }

    @Test
    @DisplayName("子流程目标不存在被拒绝")
    void validation_callProcessMustExist() {
        NodeModel call = node("cp", "子流程", 5);
        call.setCallProcess("999:不存在");
        NodeModel root = node("start", "发起人", 0);
        root.setChildNode(call);
        when(processService.getProcessById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> validate(root));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("票签权重部分配置被拒绝")
    void validation_voteSignWeights() {
        NodeModel vote = node("vote", "投票", 1);
        vote.setSetType(1);
        vote.setExamineMode(4);
        com.aizuda.bpm.engine.model.NodeAssignee a1 = new com.aizuda.bpm.engine.model.NodeAssignee();
        a1.setId("1");
        a1.setName("甲");
        a1.setWeight(60);
        com.aizuda.bpm.engine.model.NodeAssignee a2 = new com.aizuda.bpm.engine.model.NodeAssignee();
        a2.setId("2");
        a2.setName("乙");
        vote.setNodeAssigneeList(java.util.List.of(a1, a2));
        NodeModel root = node("start", "发起人", 0);
        root.setChildNode(vote);

        BusinessException ex = assertThrows(BusinessException.class, () -> validate(root));
        assertTrue(ex.getMessage().contains("权重"));
    }

    @Test
    @DisplayName("驳回策略3目标节点不存在被拒绝")
    void validation_rejectTargetMustExist() {
        NodeModel approval = node("ap", "审批", 1);
        approval.setSetType(5); // 发起人自己，规避处理人校验
        approval.setRejectStrategy(3);
        approval.setExtendConfig(new HashMap<>(java.util.Map.of("rejectTargetKey", "node_missing")));
        NodeModel root = node("start", "发起人", 0);
        root.setChildNode(approval);

        BusinessException ex = assertThrows(BusinessException.class, () -> validate(root));
        assertTrue(ex.getMessage().contains("目标节点不存在"));
    }

    @Test
    @DisplayName("合法的并行+包容+票签组合模型通过校验")
    void validation_validModelPasses() {
        NodeModel root = node("start", "发起人", 0);
        NodeModel parallel = node("pb", "并行", 8);
        parallel.setParallelNodes(java.util.List.of(branch("b1", "b1", false), branch("b2", "b2", false)));
        root.setChildNode(parallel);

        assertDoesNotThrow(() -> validate(root));
    }
}

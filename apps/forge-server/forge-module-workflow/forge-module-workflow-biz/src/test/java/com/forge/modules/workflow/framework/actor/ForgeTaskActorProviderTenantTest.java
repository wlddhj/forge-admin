package com.forge.modules.workflow.framework.actor;

import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 候选人策略租户传递单元测试（评审 §4.5-P1#5+#6）：
 * 异步链路（AI 审批推进/超时自动办理/AFTER_COMMIT 抄送）无 Web 上下文时，
 * 策略以实例行租户快照执行；getStartUserId 返回发起人而非操作者。
 */
class ForgeTaskActorProviderTenantTest {

    private BpmTaskCandidateInvoker candidateInvoker;
    private FlowLongIdentityService identityService;
    private ForgeTaskActorProvider provider;

    /** 捕获策略执行时刻的租户上下文 */
    private final java.util.concurrent.atomic.AtomicReference<Long> tenantDuringCalculate =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() {
        candidateInvoker = mock(BpmTaskCandidateInvoker.class);
        identityService = mock(FlowLongIdentityService.class);
        provider = new ForgeTaskActorProvider(candidateInvoker, identityService);
        // 策略执行时记录当前租户并返回固定候选人
        when(candidateInvoker.calculateUsers(anyInt(), anyString(), any())).thenAnswer(inv -> {
            tenantDuringCalculate.set(TenantContextHolder.getTenantId());
            return Set.of(9L);
        });
        when(identityService.getUserName(9L)).thenReturn("候选人9");
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** 指定成员节点（setType=1 → User 策略），实例租户=2 */
    private NodeModel userNode() {
        NodeModel node = new NodeModel();
        node.setNodeName("指定审批");
        node.setSetType(1); // NodeSetType.specifyMembers（User 策略）
        node.setNodeAssigneeList(List.of(new com.aizuda.bpm.engine.model.NodeAssignee() {{
            setId("9");
            setName("候选人9");
        }}));
        return node;
    }

    private com.aizuda.bpm.engine.core.Execution execution(String instanceTenantId, String createId, String operatorId) {
        FlwInstance instance = new FlwInstance();
        instance.setId(200L);
        instance.setTenantId(instanceTenantId);
        instance.setCreateId(createId);
        com.aizuda.bpm.engine.core.Execution execution = mock(com.aizuda.bpm.engine.core.Execution.class);
        when(execution.getFlwInstance()).thenReturn(instance);
        com.aizuda.bpm.engine.core.FlowCreator creator =
                com.aizuda.bpm.engine.core.FlowCreator.of(operatorId, "操作人");
        when(execution.getFlowCreator()).thenReturn(creator);
        when(execution.getArgs()).thenReturn(Map.of());
        return execution;
    }

    @Test
    @DisplayName("无租户上下文（异步链路模拟）：策略以实例行租户执行且结束后上下文不残留")
    void strategyShouldRunUnderInstanceTenantSnapshot() throws Exception {
        com.aizuda.bpm.engine.core.Execution execution = execution("2", "1", "8");

        // 模拟异步链路：当前线程无任何租户上下文
        List<FlwTaskActor> actors = provider.getTaskActors(userNode(), execution);

        assertEquals(1, actors.size());
        assertEquals(2L, tenantDuringCalculate.get(), "策略执行时租户应为实例行租户 2");
        assertNull(TenantContextHolder.getTenantId(), "结束后不得残留快照租户");
    }

    @Test
    @DisplayName("实例租户为非数字串：按无租户处理（策略空转走父类默认）")
    void invalidTenantIdShouldNotInject() throws Exception {
        com.aizuda.bpm.engine.core.Execution execution = execution("tenant-a", "1", "8");
        provider.getTaskActors(userNode(), execution);
        assertNull(tenantDuringCalculate.get(), "非数字租户不得注入上下文");
    }

    @Test
    @DisplayName("getStartUserId 返回实例发起人而非本次操作者（P1#6）")
    void startUserIdShouldComeFromInstanceCreateId() throws Exception {
        // 实例发起人=1，本次操作者=8（上一环节审批人）——旧实现误返 8
        com.aizuda.bpm.engine.core.Execution execution = execution("2", "1", "8");
        provider.getTaskActors(userNode(), execution);

        ArgumentCaptor<BpmTaskCandidateStrategy.TaskContext> captor =
                ArgumentCaptor.forClass(BpmTaskCandidateStrategy.TaskContext.class);
        verify(candidateInvoker, times(1)).calculateUsers(anyInt(), anyString(), captor.capture());
        assertEquals(1L, captor.getValue().getStartUserId(), "发起人应为实例 createId=1 而非操作者 8");
    }

    @Test
    @DisplayName("Web 链路（上下文已有租户 3）：快照为同值 no-op，结束后恢复原值")
    void webContextShouldBeNoOp() throws Exception {
        TenantContextHolder.setTenantId(3L);
        com.aizuda.bpm.engine.core.Execution execution = execution("3", "1", "8");
        provider.getTaskActors(userNode(), execution);
        assertEquals(3L, tenantDuringCalculate.get());
        assertEquals(3L, TenantContextHolder.getTenantId(), "web 链路原上下文应保持");
    }

    @Test
    @DisplayName("租户快照跨线程传递：异步任务内执行策略获得正确租户（CompletableFuture 模拟异步链路）")
    void snapshotShouldWorkFromPlainThread() throws Exception {
        com.aizuda.bpm.engine.core.Execution execution = execution("5", "1", "8");
        // 普通线程池（未 TTL 包装）模拟 Quartz/异步消费线程：无租户上下文
        CompletableFuture.supplyAsync(() -> {
            try {
                return provider.getTaskActors(userNode(), execution);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get(5, TimeUnit.SECONDS);
        assertEquals(5L, tenantDuringCalculate.get(), "普通异步线程内策略租户应为实例行租户 5");
    }
}

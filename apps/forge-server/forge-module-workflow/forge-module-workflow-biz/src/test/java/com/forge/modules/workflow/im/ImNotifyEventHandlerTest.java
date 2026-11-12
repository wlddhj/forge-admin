package com.forge.modules.workflow.im;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.entity.WfImChannelConfig;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import com.forge.modules.workflow.mapper.WfImChannelConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事件处理器单测：事件类型过滤、参与者解析、发起人解析失败跳过、文案路由、
 * 租户来源解析（事件优先/上下文回退/无来源 fail-closed/处理段上下文包裹）
 */
class ImNotifyEventHandlerTest {

    private ReliableEventConsumer consumer;
    private ImNotificationRouter router;
    private ImMessageBuilder messageBuilder;
    private ProcessService processService;
    private QueryService queryService;
    private ImNotifyEventHandler handler;
    private com.forge.framework.tenant.config.TenantProperties tenantProperties;

    @BeforeEach
    void setUp() {
        tenantProperties = new com.forge.framework.tenant.config.TenantProperties();
        consumer = mock(ReliableEventConsumer.class);
        // consume 直跑 Runnable（与真实重试模板的立即首试一致）
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(consumer).consume(anyString(), any(), any(Runnable.class));

        router = mock(ImNotificationRouter.class);
        messageBuilder = mock(ImMessageBuilder.class);
        processService = mock(ProcessService.class);
        queryService = mock(QueryService.class);
        handler = new ImNotifyEventHandler(consumer, router, messageBuilder, processService, queryService,
                tenantProperties);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private FlwTaskActor actor(String id) {
        FlwTaskActor actor = new FlwTaskActor();
        actor.setActorId(id);
        actor.setActorType(ActorType.user.getValue());
        return actor;
    }

    private WorkflowTaskEvent taskEvent(TaskEventType type, List<FlwTaskActor> actors) {
        return taskEvent(type, actors, 2L);
    }

    private WorkflowTaskEvent taskEvent(TaskEventType type, List<FlwTaskActor> actors, Long tenantId) {
        FlwTask task = new FlwTask();
        task.setId(11L);
        task.setTaskName("审批节点A");
        task.setInstanceId(22L);
        task.setTaskKey("node_a");
        return new WorkflowTaskEvent(this, type, task, actors, null, null, tenantId);
    }

    private WorkflowInstanceEvent instanceEvent(InstanceEventType type, FlwHisInstance his) {
        return new WorkflowInstanceEvent(this, type, his, null, null, 2L);
    }

    @Test
    void onTaskEvent_Create_DispatchesToActors() {
        FlwInstance instance = new FlwInstance();
        instance.setProcessId(33L);
        when(queryService.getInstance(22L)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setProcessKey("demo_process");
        process.setProcessName("项目立项");
        when(processService.getProcessById(33L)).thenReturn(process);
        ImNotificationMessage msg = new ImNotificationMessage("taskCreated", "t", "c", "u", 11L, 22L, "demo_process");
        when(messageBuilder.buildTaskMessage(eq(TaskEventType.create), any(), eq("demo_process"), eq("项目立项")))
                .thenReturn(msg);

        handler.onTaskEvent(taskEvent(TaskEventType.create, List.of(actor("1"), actor("2"), actor("abc"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> usersCaptor = ArgumentCaptor.forClass((Class<List<Long>>) (Class<?>) List.class);
        verify(router).dispatch(eq(msg), usersCaptor.capture());
        // 非数字 actorId（如角色/部门 actor）被跳过
        assertEquals(List.of(1L, 2L), usersCaptor.getValue());
    }

    @Test
    void onTaskEvent_Complete_NotPushed() {
        handler.onTaskEvent(taskEvent(TaskEventType.complete, List.of(actor("1"))));
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onTaskEvent_Reject_NotPushed() {
        handler.onTaskEvent(taskEvent(TaskEventType.reject, List.of(actor("1"))));
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onTaskEvent_Timeout_Dispatched() {
        when(queryService.getInstance(22L)).thenReturn(null);
        ImNotificationMessage msg = new ImNotificationMessage("taskTimeout", "t", "c", "u", 11L, 22L, null);
        when(messageBuilder.buildTaskMessage(eq(TaskEventType.timeout), any(), eq(null), eq(null)))
                .thenReturn(msg);

        handler.onTaskEvent(taskEvent(TaskEventType.timeout, List.of(actor("3"))));

        verify(router).dispatch(eq(msg), eq(List.of(3L)));
    }

    @Test
    void onTaskEvent_NoUserActors_NoDispatch() {
        handler.onTaskEvent(taskEvent(TaskEventType.create, List.of()));
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onInstanceEvent_Terminal_NotifiesInitiator() {
        FlwHisInstance his = new FlwHisInstance();
        his.setId(22L);
        his.setCreateId("9");
        his.setProcessId(33L);
        when(processService.getProcessById(33L)).thenReturn(null);
        ImNotificationMessage msg = new ImNotificationMessage("instanceFinished", "t", "c", "u", null, 22L, null);
        when(messageBuilder.buildInstanceMessage(eq(InstanceEventType.end), eq(his), eq(null), eq(null)))
                .thenReturn(msg);

        handler.onInstanceEvent(instanceEvent(InstanceEventType.end, his));

        verify(router).dispatch(eq(msg), eq(List.of(9L)));
    }

    @Test
    void onInstanceEvent_NonTerminal_Ignored() {
        FlwHisInstance his = new FlwHisInstance();
        his.setId(22L);
        handler.onInstanceEvent(instanceEvent(InstanceEventType.start, his));
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onInstanceEvent_InitiatorNotParsable_Skips() {
        FlwHisInstance his = new FlwHisInstance();
        his.setId(22L);
        his.setCreateId("not-a-number");

        handler.onInstanceEvent(instanceEvent(InstanceEventType.end, his));

        verify(router, never()).dispatch(any(), anyCollection());
        assertTrue(true);
    }

    // ========== 租户来源解析（事件优先 / 上下文回退 / 无来源 fail-closed） ==========

    @Test
    void onTaskEvent_EventTenantWinsOverThreadContext_AndRestores() {
        // 事件租户 2 优先于线程上下文 3；处理段内读到 2，结束后线程恢复 3
        TenantContextHolder.setTenantId(3L);
        AtomicReference<Long> dispatchTenant = new AtomicReference<>();
        doAnswer(invocation -> {
            dispatchTenant.set(TenantContextHolder.getTenantId());
            return null;
        }).when(router).dispatch(any(), anyCollection());

        handler.onTaskEvent(taskEvent(TaskEventType.create, List.of(actor("1")), 2L));

        assertEquals(2L, dispatchTenant.get(), "处理段应以事件租户为准");
        assertEquals(3L, TenantContextHolder.getTenantId(), "结束后应恢复线程原租户");
    }

    @Test
    void onTaskEvent_EventTenantNull_FallsBackToThreadContext() {
        TenantContextHolder.setTenantId(4L);
        when(queryService.getInstance(22L)).thenReturn(null);
        ImNotificationMessage msg = new ImNotificationMessage("taskCreated", "t", "c", "u", 11L, 22L, null);
        when(messageBuilder.buildTaskMessage(eq(TaskEventType.create), any(), isNull(), isNull())).thenReturn(msg);
        AtomicReference<Long> dispatchTenant = new AtomicReference<>();
        doAnswer(invocation -> {
            dispatchTenant.set(TenantContextHolder.getTenantId());
            return null;
        }).when(router).dispatch(any(), anyCollection());

        handler.onTaskEvent(taskEvent(TaskEventType.create, List.of(actor("1")), null));

        assertEquals(4L, dispatchTenant.get(), "事件无租户时应回退线程上下文");
        verify(router).dispatch(eq(msg), eq(List.of(1L)));
    }

    @Test
    void onTaskEvent_TenantDisabled_NoSourceFallsBackToDefaultTenant() {
        // 关闸（单租户部署）无任何租户来源：保持历史行为按默认租户 1 推送，不得因 fail-closed 改造而停发
        tenantProperties.setEnable(false);
        when(queryService.getInstance(22L)).thenReturn(null);
        ImNotificationMessage msg = new ImNotificationMessage("taskCreated", "t", "c", "u", 11L, 22L, null);
        when(messageBuilder.buildTaskMessage(eq(TaskEventType.create), any(), isNull(), isNull())).thenReturn(msg);
        AtomicReference<Long> dispatchTenant = new AtomicReference<>();
        doAnswer(invocation -> {
            dispatchTenant.set(TenantContextHolder.getTenantId());
            return null;
        }).when(router).dispatch(any(), anyCollection());

        handler.onTaskEvent(taskEvent(TaskEventType.create, List.of(actor("1")), null));

        assertEquals(1L, dispatchTenant.get(), "关闸无来源应兜底默认租户 1（单租户部署语义）");
        verify(router).dispatch(eq(msg), eq(List.of(1L)));
    }

    @Test
    void onTaskEvent_NoTenantSource_FailClosedSkip() {
        // 事件租户 null + 线程上下文 null → warn 跳过（不发送、不抛异常、不进消费模板）
        TenantContextHolder.clear();

        assertDoesNotThrow(() ->
                handler.onTaskEvent(taskEvent(TaskEventType.create, List.of(actor("1")), null)));

        verify(router, never()).dispatch(any(), anyCollection());
        verify(consumer, never()).consume(anyString(), any(), any(Runnable.class));
    }

    @Test
    void onInstanceEvent_NoTenantSource_FailClosedSkip() {
        TenantContextHolder.clear();
        FlwHisInstance his = new FlwHisInstance();
        his.setId(22L);
        his.setCreateId("9");

        assertDoesNotThrow(() ->
                handler.onInstanceEvent(new WorkflowInstanceEvent(this, InstanceEventType.end, his, null, null)));

        verify(router, never()).dispatch(any(), anyCollection());
        verify(consumer, never()).consume(anyString(), any(), any(Runnable.class));
    }

    @Test
    void onTaskEvent_Tenant2Event_UsesOnlyTenant2ChannelConfig() {
        // 全链（真实 router + 真实渠道配置服务 + 桩渠道/mapper）：租户 2 事件仅查询/使用租户 2
        // 渠道配置；租户 1 有配置（mapper 对任意租户都返回完整行）也不串
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                WfImChannelConfig.class);
        WfImChannelConfigMapper configMapper = mock(WfImChannelConfigMapper.class);
        Set<Long> queriedTenants = ConcurrentHashMap.newKeySet();
        when(configMapper.selectOne(any())).thenAnswer(invocation -> {
            Long tenant = tenantParamOf(invocation.getArgument(0));
            queriedTenants.add(tenant);
            return completeRow(tenant, "wework");
        });
        ImChannelConfigService realConfigService = new ImChannelConfigService(configMapper);
        List<Long> sentTenants = new CopyOnWriteArrayList<>();
        ImNotificationChannel stubChannel = new ImNotificationChannel() {
            @Override
            public String channel() {
                return "wework";
            }

            @Override
            public boolean enabled(Long tenantId) {
                return realConfigService.runnable(tenantId, channel());
            }

            @Override
            public ImSendResult sendToUser(Long tenantId, String corpUserid, ImNotificationMessage message) {
                sentTenants.add(tenantId);
                return ImSendResult.ok();
            }
        };
        ImUserBindingResolver bindingResolver = mock(ImUserBindingResolver.class);
        when(bindingResolver.resolve(eq("wework"), eq(2L), anyCollection())).thenReturn(Map.of(1L, "ww-1"));
        ImNotificationRouter realRouter = new ImNotificationRouter(List.of(stubChannel), bindingResolver,
                realConfigService);
        ImNotifyEventHandler realHandler = new ImNotifyEventHandler(consumer, realRouter, messageBuilder,
                processService, queryService, tenantProperties);
        when(queryService.getInstance(22L)).thenReturn(null);
        ImNotificationMessage msg = new ImNotificationMessage("taskCreated", "t", "c", "u", 11L, 22L, null);
        when(messageBuilder.buildTaskMessage(eq(TaskEventType.create), any(), isNull(), isNull())).thenReturn(msg);

        // 线程上下文为租户 1（失真场景）：事件租户 2 仍必须胜出
        TenantContextHolder.setTenantId(1L);
        realHandler.onTaskEvent(taskEvent(TaskEventType.create, List.of(actor("1")), 2L));

        assertEquals(List.of(2L), sentTenants, "租户 2 事件应仅以租户 2 渠道配置发送");
        assertTrue(queriedTenants.contains(2L), "应查询租户 2 渠道配置");
        assertFalse(queriedTenants.contains(1L), "租户 1 配置存在也不得被查询（不串租户）");
        assertEquals(1L, TenantContextHolder.getTenantId(), "结束后应恢复线程原租户");
    }

    /**
     * 从 LambdaQueryWrapper 提取 tenant_id 等值条件参数（需 TableInfo 初始化后 getSqlSegment 物化）
     */
    private Long tenantParamOf(Wrapper<WfImChannelConfig> wrapper) {
        if (wrapper instanceof AbstractWrapper<?, ?, ?> aw) {
            aw.getSqlSegment();
            return aw.getParamNameValuePairs().values().stream()
                    .filter(value -> value instanceof Long)
                    .map(value -> (Long) value)
                    .findFirst().orElse(null);
        }
        return null;
    }

    private WfImChannelConfig completeRow(Long tenantId, String channel) {
        WfImChannelConfig row = new WfImChannelConfig();
        row.setTenantId(tenantId);
        row.setChannel(channel);
        row.setEnabled(1);
        row.setCorpId("corp-" + tenantId);
        row.setAgentId("100");
        row.setSecret("secret-" + tenantId);
        return row;
    }
}

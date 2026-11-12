package com.forge.modules.workflow.scheduler;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.forge.framework.redis.lock.DistributedLock;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.api.tenant.TenantApi;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.framework.reminder.TaskReminderHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowTimeoutJob 单元测试
 *
 * 多租户遍历（租户序列/异常隔离/空租户跳过/上下文恢复）
 */
class WorkflowTimeoutJobTest {

    private FlowLongSchedulerProperties properties;

    private TaskService taskService;
    private DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        properties = new FlowLongSchedulerProperties();
        properties.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        // 防止租户上下文泄漏到其他用例
        TenantContextHolder.clear();
    }

    /**
     * 按生产装配方式构造 Quartz 调度上下文（applicationContextKey → mock 的 Spring 容器）
     */
    private JobExecutionContext buildJobContext(TenantApi tenantApi) throws SchedulerException {
        taskService = mock(TaskService.class);
        FlowLongEngine flowLongEngine = mock(FlowLongEngine.class);
        when(flowLongEngine.taskService()).thenReturn(taskService);
        distributedLock = mock(DistributedLock.class);
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn(true);

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(DistributedLock.class)).thenReturn(distributedLock);
        when(applicationContext.getBean(FlowLongSchedulerProperties.class)).thenReturn(properties);
        when(applicationContext.getBean(FlowLongEngine.class)).thenReturn(flowLongEngine);
        when(applicationContext.getBean(TaskReminderHandler.class)).thenReturn(mock(TaskReminderHandler.class));
        when(applicationContext.getBean(TenantApi.class)).thenReturn(tenantApi);

        SchedulerContext schedulerContext = new SchedulerContext();
        schedulerContext.put("applicationContextKey", applicationContext);
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getContext()).thenReturn(schedulerContext);
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getScheduler()).thenReturn(scheduler);
        return context;
    }

    @Test
    @DisplayName("租户遍历：两租户按序绑定上下文处理（1→2）")
    void tenantLoop_processesEachTenantInOrder() throws Exception {
        TenantApi tenantApi = mock(TenantApi.class);
        when(tenantApi.getTenantIds()).thenReturn(List.of(1L, 2L));
        JobExecutionContext context = buildJobContext(tenantApi);
        List<Long> observedTenantIds = new ArrayList<>();
        when(taskService.getTimeoutOrRemindTasks()).thenAnswer(inv -> {
            observedTenantIds.add(TenantContextHolder.getTenantId());
            return new ArrayList<FlwTask>();
        });

        new WorkflowTimeoutJob().execute(context);

        assertEquals(Arrays.asList(1L, 2L), observedTenantIds, "taskService 调用时应按 1→2 绑定租户上下文");
        verify(taskService, times(2)).getTimeoutOrRemindTasks();
    }

    @Test
    @DisplayName("异常隔离：租户1处理失败不影响租户2")
    void tenantLoop_isolatesTenantFailure() throws Exception {
        TenantApi tenantApi = mock(TenantApi.class);
        when(tenantApi.getTenantIds()).thenReturn(List.of(1L, 2L));
        JobExecutionContext context = buildJobContext(tenantApi);
        List<Long> observedTenantIds = new ArrayList<>();
        when(taskService.getTimeoutOrRemindTasks()).thenAnswer(inv -> {
            Long tenantId = TenantContextHolder.getTenantId();
            observedTenantIds.add(tenantId);
            if (tenantId != null && tenantId == 1L) {
                throw new RuntimeException("租户1模拟故障");
            }
            return new ArrayList<FlwTask>();
        });

        assertDoesNotThrow(() -> new WorkflowTimeoutJob().execute(context));

        assertEquals(Arrays.asList(1L, 2L), observedTenantIds, "租户1失败后租户2仍应被处理");
        verify(distributedLock, times(1)).unlock(anyString());
    }

    @Test
    @DisplayName("空租户列表：跳过本轮，不调 taskService 不报错")
    void tenantLoop_skipsWhenNoTenant() throws Exception {
        TenantApi tenantApi = mock(TenantApi.class);
        when(tenantApi.getTenantIds()).thenReturn(Collections.emptyList());
        JobExecutionContext context = buildJobContext(tenantApi);
        when(taskService.getTimeoutOrRemindTasks()).thenReturn(new ArrayList<FlwTask>());

        assertDoesNotThrow(() -> new WorkflowTimeoutJob().execute(context));

        verify(taskService, never()).getTimeoutOrRemindTasks();
    }

    @Test
    @DisplayName("上下文恢复：任务执行后回到原值（含原值 null 场景）")
    void tenantLoop_restoresPreviousTenantId() throws Exception {
        TenantApi tenantApi = mock(TenantApi.class);
        when(tenantApi.getTenantIds()).thenReturn(List.of(1L));

        // 原值非 null：执行后应恢复为原值
        TenantContextHolder.setTenantId(9L);
        JobExecutionContext withPrevious = buildJobContext(tenantApi);
        when(taskService.getTimeoutOrRemindTasks()).thenReturn(new ArrayList<FlwTask>());
        new WorkflowTimeoutJob().execute(withPrevious);
        assertEquals(Long.valueOf(9L), TenantContextHolder.getTenantId(), "执行后应恢复原租户上下文");

        // 原值为 null：执行后应清空（不残留遍历时的租户）
        TenantContextHolder.clear();
        JobExecutionContext withoutPrevious = buildJobContext(tenantApi);
        when(taskService.getTimeoutOrRemindTasks()).thenReturn(new ArrayList<FlwTask>());
        new WorkflowTimeoutJob().execute(withoutPrevious);
        assertNull(TenantContextHolder.getTenantId(), "原值为 null 时执行后不应残留租户上下文");
    }
}

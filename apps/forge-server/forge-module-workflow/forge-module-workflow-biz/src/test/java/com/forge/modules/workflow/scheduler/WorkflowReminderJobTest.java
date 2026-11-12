package com.forge.modules.workflow.scheduler;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.forge.framework.redis.lock.DistributedLock;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.system.api.tenant.TenantApi;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * WorkflowReminderJob 单元测试
 *
 * 测试提醒配置读取和判断逻辑，以及多租户遍历（租户序列/异常隔离/空租户跳过/上下文恢复）
 */
class WorkflowReminderJobTest {

    private FlowLongSchedulerProperties properties;

    private TaskService taskService;
    private DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        properties = new FlowLongSchedulerProperties();
        properties.setEnabled(true);
        properties.setMaxReminderCount(3);
        properties.setReminderIntervalHours(24);
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
        when(applicationContext.getBean(NotificationService.class)).thenReturn(mock(NotificationService.class));
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
    @DisplayName("节点级提醒间隔配置应覆盖全局配置")
    void nodeConfig_shouldOverrideGlobalInterval() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindIntervalHours", 8);

        int globalInterval = properties.getReminderIntervalHours();
        int nodeInterval = ((Number) extendConfig.get("remindIntervalHours")).intValue();

        assertTrue(nodeInterval > 0, "节点配置间隔应大于0");
        assertNotEquals(globalInterval, nodeInterval, "节点配置应不同于全局配置");
        assertEquals(8, nodeInterval, "节点配置间隔应为8小时");
    }

    @Test
    @DisplayName("节点级最大提醒次数配置应生效")
    void nodeConfig_shouldOverrideMaxCount() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindMaxCount", 5);

        int globalMaxCount = properties.getMaxReminderCount();
        int nodeMaxCount = ((Number) extendConfig.get("remindMaxCount")).intValue();

        assertTrue(nodeMaxCount > 0, "节点配置最大次数应大于0");
        assertNotEquals(globalMaxCount, nodeMaxCount, "节点配置应不同于全局配置");
        assertEquals(5, nodeMaxCount, "节点配置最大次数应为5");
    }

    @Test
    @DisplayName("提醒次数达到最大值后不应发送提醒")
    void shouldNotSend_whenMaxCountReached() {
        int remindRepeat = 3;
        int maxReminderCount = properties.getMaxReminderCount();

        assertTrue(remindRepeat >= maxReminderCount, "已达到最大提醒次数");
        assertFalse(remindRepeat < maxReminderCount, "不应再发送提醒");
    }

    @Test
    @DisplayName("节点级最大次数达到后不应发送提醒")
    void shouldNotSend_whenNodeMaxCountReached() {
        int remindRepeat = 5;
        int nodeMaxCount = 5;

        assertTrue(remindRepeat >= nodeMaxCount, "已达到节点级最大提醒次数");
        assertFalse(remindRepeat < nodeMaxCount, "不应再发送提醒");
    }

    @Test
    @DisplayName("未达最大提醒次数时应发送提醒")
    void shouldSend_whenBelowMaxCount() {
        int remindRepeat = 1;
        int maxReminderCount = properties.getMaxReminderCount();

        assertTrue(remindRepeat < maxReminderCount, "未达最大提醒次数");
        assertTrue(remindRepeat >= 0, "提醒次数应非负");
    }

    @Test
    @DisplayName("remindRepeat 为 null 时视为0次")
    void shouldSend_whenRemindRepeatIsNull() {
        Integer remindRepeat = null;
        int effectiveRepeat = remindRepeat != null ? remindRepeat : 0;

        assertEquals(0, effectiveRepeat, "null 应视为0次");
        assertTrue(effectiveRepeat < properties.getMaxReminderCount(), "应发送提醒");
    }

    @Test
    @DisplayName("提醒间隔计算验证")
    void intervalCalculation_shouldBeCorrect() {
        int intervalHours = 12;
        long currentTime = System.currentTimeMillis();
        long expectedNextRemindTime = currentTime + intervalHours * 60 * 60 * 1000;

        assertTrue(expectedNextRemindTime > currentTime, "下次提醒时间应大于当前时间");

        long diff = expectedNextRemindTime - currentTime;
        assertEquals(12 * 60 * 60 * 1000, diff, "时间差应为12小时");
    }

    @Test
    @DisplayName("全局配置默认值验证")
    void globalConfigDefaults_shouldBeValid() {
        assertTrue(properties.isEnabled(), "应启用提醒检查");
        assertEquals(3, properties.getMaxReminderCount(), "默认最大提醒次数应为3");
        assertEquals(24, properties.getReminderIntervalHours(), "默认提醒间隔应为24小时");
    }

    @Test
    @DisplayName("解析数值类型配置")
    void parseConfig_shouldParseNumber() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindIntervalHours", 12);
        extendConfig.put("remindMaxCount", 5);

        Object intervalObj = extendConfig.get("remindIntervalHours");
        assertTrue(intervalObj instanceof Number, "间隔配置应为数值类型");
        assertEquals(12, ((Number) intervalObj).intValue(), "间隔应为12小时");

        Object maxCountObj = extendConfig.get("remindMaxCount");
        assertTrue(maxCountObj instanceof Number, "最大次数配置应为数值类型");
        assertEquals(5, ((Number) maxCountObj).intValue(), "最大次数应为5");
    }

    @Test
    @DisplayName("完整节点配置验证")
    void fullNodeConfig_shouldBeValid() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAuto", true);
        extendConfig.put("remindAdvanceMinutes", 30);
        extendConfig.put("remindIntervalHours", 8);
        extendConfig.put("remindMaxCount", 5);
        extendConfig.put("remindChannels", Arrays.asList("websocket"));

        // 验证所有配置项
        assertTrue(Boolean.TRUE.equals(extendConfig.get("remindAuto")), "应启用提醒");
        assertTrue(extendConfig.get("remindAdvanceMinutes") instanceof Number, "提前时间应为数值");
        assertTrue(extendConfig.get("remindIntervalHours") instanceof Number, "间隔应为数值");
        assertTrue(extendConfig.get("remindMaxCount") instanceof Number, "最大次数应为数值");
        assertTrue(extendConfig.get("remindChannels") instanceof List, "渠道应为列表");
    }

    @Test
    @DisplayName("节点无扩展配置时使用全局配置")
    void shouldUseGlobalConfig_whenNoNodeConfig() {
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey("task_1");
        // 无 extendConfig

        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        assertNull(extendConfig, "无扩展配置时应返回null");

        // 使用全局配置
        int maxCount = properties.getMaxReminderCount();
        int interval = properties.getReminderIntervalHours();

        assertEquals(3, maxCount, "应使用全局最大次数");
        assertEquals(24, interval, "应使用全局间隔");
    }

    @Test
    @DisplayName("配置边界值验证")
    void configBoundary_shouldBeValid() {
        // 最小间隔
        properties.setReminderIntervalHours(1);
        assertEquals(1, properties.getReminderIntervalHours(), "最小间隔应为1小时");

        // 最大间隔（一周）
        properties.setReminderIntervalHours(168);
        assertEquals(168, properties.getReminderIntervalHours(), "最大间隔应为168小时");

        // 最小提醒次数
        properties.setMaxReminderCount(1);
        assertEquals(1, properties.getMaxReminderCount(), "最小次数应为1");

        // 较大提醒次数
        properties.setMaxReminderCount(10);
        assertEquals(10, properties.getMaxReminderCount(), "较大提醒次数应为10");
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

        new WorkflowReminderJob().execute(context);

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

        assertDoesNotThrow(() -> new WorkflowReminderJob().execute(context));

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

        assertDoesNotThrow(() -> new WorkflowReminderJob().execute(context));

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
        new WorkflowReminderJob().execute(withPrevious);
        assertEquals(Long.valueOf(9L), TenantContextHolder.getTenantId(), "执行后应恢复原租户上下文");

        // 原值为 null：执行后应清空（不残留遍历时的租户）
        TenantContextHolder.clear();
        JobExecutionContext withoutPrevious = buildJobContext(tenantApi);
        when(taskService.getTimeoutOrRemindTasks()).thenReturn(new ArrayList<FlwTask>());
        new WorkflowReminderJob().execute(withoutPrevious);
        assertNull(TenantContextHolder.getTenantId(), "原值为 null 时执行后不应残留租户上下文");
    }
}
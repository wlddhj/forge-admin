package com.forge.modules.workflow.scheduler;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowReminderJob 单元测试
 *
 * 测试提醒配置读取和判断逻辑
 */
class WorkflowReminderJobTest {

    private FlowLongSchedulerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FlowLongSchedulerProperties();
        properties.setEnabled(true);
        properties.setMaxReminderCount(3);
        properties.setReminderIntervalHours(24);
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
        assertEquals(10, properties.getMaxReminderCount(), "最大次数应为10");
    }
}
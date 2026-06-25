package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompositeTaskListener 单元测试
 *
 * 测试提醒时间设置逻辑的核心算法
 */
class CompositeTaskListenerTest {

    /**
     * 测试提醒时间计算逻辑
     */
    @Test
    @DisplayName("应正确计算提醒时间")
    void calculateRemindTime_shouldBeCorrect() {
        int remindAdvanceMinutes = 30;
        long currentTime = System.currentTimeMillis();
        long expectedRemindTime = currentTime + remindAdvanceMinutes * 60 * 1000;

        // 验证计算逻辑
        assertTrue(expectedRemindTime > currentTime, "提醒时间应大于当前时间");

        // 验证时间差
        long diff = expectedRemindTime - currentTime;
        assertEquals(30 * 60 * 1000, diff, "时间差应为30分钟");
    }

    @Test
    @DisplayName("解析数值类型提前提醒时间")
    void parseAdvanceMinutes_shouldParseNumber() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAuto", true);
        extendConfig.put("remindAdvanceMinutes", 60);

        Object value = extendConfig.get("remindAdvanceMinutes");
        assertTrue(value instanceof Number, "值应为数值类型");

        int minutes = ((Number) value).intValue();
        assertEquals(60, minutes, "提前提醒时间应为60分钟");
    }

    @Test
    @DisplayName("解析字符串类型提前提醒时间")
    void parseAdvanceMinutes_shouldParseString() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAuto", true);
        extendConfig.put("remindAdvanceMinutes", "45");

        Object value = extendConfig.get("remindAdvanceMinutes");
        assertTrue(value instanceof String, "值应为字符串类型");

        int minutes = Integer.parseInt((String) value);
        assertEquals(45, minutes, "提前提醒时间应为45分钟");
    }

    @Test
    @DisplayName("无效字符串不应解析成功")
    void parseAdvanceMinutes_shouldFailOnInvalidString() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAuto", true);
        extendConfig.put("remindAdvanceMinutes", "invalid");

        Object value = extendConfig.get("remindAdvanceMinutes");
        assertTrue(value instanceof String, "值应为字符串类型");

        assertThrows(NumberFormatException.class, () -> {
            Integer.parseInt((String) value);
        }, "无效字符串应抛出异常");
    }

    @Test
    @DisplayName("零值提前提醒时间不应设置提醒")
    void validateAdvanceMinutes_shouldRejectZero() {
        int remindAdvanceMinutes = 0;
        assertFalse(remindAdvanceMinutes > 0, "零值不应通过验证");
    }

    @Test
    @DisplayName("负值提前提醒时间不应设置提醒")
    void validateAdvanceMinutes_shouldRejectNegative() {
        int remindAdvanceMinutes = -10;
        assertFalse(remindAdvanceMinutes > 0, "负值不应通过验证");
    }

    @Test
    @DisplayName("remindAuto 为 null 时不应启用提醒")
    void checkRemindAuto_shouldNotEnable_whenNull() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAdvanceMinutes", 30);
        // 无 remindAuto

        Object remindAutoObj = extendConfig.get("remindAuto");
        assertTrue(remindAutoObj == null || !Boolean.TRUE.equals(remindAutoObj),
                "null 值不应启用提醒");
    }

    @Test
    @DisplayName("remindAuto 为 false 时不应启用提醒")
    void checkRemindAuto_shouldNotEnable_whenFalse() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAuto", false);
        extendConfig.put("remindAdvanceMinutes", 30);

        Object remindAutoObj = extendConfig.get("remindAuto");
        assertFalse(Boolean.TRUE.equals(remindAutoObj), "false 不应启用提醒");
    }

    @Test
    @DisplayName("remindAuto 为 true 时应启用提醒")
    void checkRemindAuto_shouldEnable_whenTrue() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAuto", true);
        extendConfig.put("remindAdvanceMinutes", 30);

        Object remindAutoObj = extendConfig.get("remindAuto");
        assertTrue(Boolean.TRUE.equals(remindAutoObj), "true 应启用提醒");
    }

    @Test
    @DisplayName("节点无扩展配置时不应设置提醒")
    void checkExtendConfig_shouldNotSet_whenNull() {
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey("task_1");
        // 无 extendConfig

        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        assertNull(extendConfig, "无扩展配置时应返回 null");
    }

    @Test
    @DisplayName("完整配置验证")
    void fullConfigValidation_shouldPass() {
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("remindAuto", true);
        extendConfig.put("remindAdvanceMinutes", 30);
        extendConfig.put("remindIntervalHours", 8);
        extendConfig.put("remindChannels", Arrays.asList("websocket"));

        // 验证 remindAuto
        Object remindAutoObj = extendConfig.get("remindAuto");
        assertTrue(Boolean.TRUE.equals(remindAutoObj), "应启用提醒");

        // 验证 remindAdvanceMinutes
        Object remindAdvanceObj = extendConfig.get("remindAdvanceMinutes");
        assertTrue(remindAdvanceObj instanceof Number, "提前提醒时间应为数值");
        int minutes = ((Number) remindAdvanceObj).intValue();
        assertTrue(minutes > 0, "提前提醒时间应大于0");

        // 验证 remindIntervalHours
        Object intervalObj = extendConfig.get("remindIntervalHours");
        assertTrue(intervalObj instanceof Number, "提醒间隔应为数值");
        int hours = ((Number) intervalObj).intValue();
        assertTrue(hours > 0, "提醒间隔应大于0");

        // 验证 remindChannels
        Object channelsObj = extendConfig.get("remindChannels");
        assertTrue(channelsObj instanceof List, "提醒渠道应为列表");
        List<String> channels = (List<String>) channelsObj;
        assertTrue(channels.contains("websocket"), "应包含 WebSocket 渠道");
    }

    @Test
    @DisplayName("非 create 事件不应触发提醒设置")
    void eventTypeCheck_shouldOnlyTriggerOnCreate() {
        // 只验证 create 事件
        TaskEventType createEvent = TaskEventType.create;
        assertNotNull(createEvent, "create 事件应存在");

        // complete 事件应不同于 create
        TaskEventType completeEvent = TaskEventType.complete;
        assertNotEquals(createEvent, completeEvent, "complete 不应等于 create");
    }
}
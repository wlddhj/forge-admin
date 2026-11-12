package com.forge.modules.workflow.listener.event;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 事件租户快照单元测试（租户开闸前置 T3）：
 * 旧签名构造器内聚烘焙发布时刻租户，异步消费端不再依赖池线程上下文。
 */
class WorkflowEventTenantSnapshotTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("旧签名构造 Task 事件自动烘焙当前租户")
    void taskEventShouldSnapshotCurrentTenant() {
        TenantContextHolder.setTenantId(2L);
        WorkflowTaskEvent event = new WorkflowTaskEvent(this, TaskEventType.create, null, null, null, null);
        assertEquals(2L, event.getTenantId());
    }

    @Test
    @DisplayName("旧签名构造 Instance 事件自动烘焙当前租户")
    void instanceEventShouldSnapshotCurrentTenant() {
        TenantContextHolder.setTenantId(3L);
        WorkflowInstanceEvent event = new WorkflowInstanceEvent(this, InstanceEventType.end, null, null, null);
        assertEquals(3L, event.getTenantId());
    }

    @Test
    @DisplayName("无租户上下文（关闸）时快照为 null")
    void eventTenantShouldBeNullWithoutContext() {
        assertNull(new WorkflowTaskEvent(this, TaskEventType.create, null, null, null, null).getTenantId());
        assertNull(new WorkflowInstanceEvent(this, InstanceEventType.end, null, null, null).getTenantId());
    }

    @Test
    @DisplayName("显式构造器可指定租户（Quartz/系统链路显式传值）")
    void explicitTenantShouldWin() {
        TenantContextHolder.setTenantId(1L);
        WorkflowTaskEvent event = new WorkflowTaskEvent(this, TaskEventType.create, null, null, null, null, 9L);
        assertEquals(9L, event.getTenantId());
    }
}

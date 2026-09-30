package com.forge.modules.workflow.framework;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowCreatorFactoryTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void user_fillsTenantIdFromContext() {
        TenantContextHolder.setTenantId(42L);
        FlowCreator creator = FlowCreatorFactory.user(1L, "张三");
        assertEquals("42", creator.getTenantId());
        assertEquals("1", creator.getCreateId());
        assertEquals("张三", creator.getCreateBy());
    }

    @Test
    void user_fallsBackToDefaultTenantWhenNoContext() {
        FlowCreator creator = FlowCreatorFactory.user(1L, "张三");
        assertEquals("1", creator.getTenantId());
    }

    @Test
    void system_fillsTenantId() {
        TenantContextHolder.setTenantId(7L);
        FlowCreator creator = FlowCreatorFactory.system("系统自动处理");
        assertEquals("7", creator.getTenantId());
        assertEquals("SYSTEM", creator.getCreateId());
        assertEquals("系统自动处理", creator.getCreateBy());
    }

    @Test
    void system_fallsBackToDefaultTenantWhenNoContext() {
        FlowCreator creator = FlowCreatorFactory.system("系统");
        assertEquals("1", creator.getTenantId());
    }
}

package com.forge.modules.workflow.framework;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.forge.framework.tenant.core.context.TenantContextHolder;

/**
 * FlowCreator 统一构建入口：租户 ID 必须随创建者传导至 FlowLong 实体（FlowEntity.tenantId），
 * 否则 flw_ 表 tenant_id 落 NULL。禁止在业务代码中直接 new FlowCreator / FlowCreator.of。
 */
public final class FlowCreatorFactory {

    private static final String DEFAULT_TENANT_ID = "1";

    private FlowCreatorFactory() {
    }

    /**
     * 构建用户创建者（发起人、审批人等）
     */
    public static FlowCreator user(Long userId, String userName) {
        return new FlowCreator(String.valueOf(userId), userName).tenantId(currentTenantId());
    }

    /**
     * 构建系统创建者（定时任务超时处理、系统自动处理等）
     */
    public static FlowCreator system(String createBy) {
        return new FlowCreator("SYSTEM", createBy).tenantId(currentTenantId());
    }

    /**
     * 当前租户 ID（字符串）：无上下文（单租户模式、Job 线程）时回落 '1'，与 sys 表 tenant_id DEFAULT 1 约定对齐
     */
    public static String currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? DEFAULT_TENANT_ID : String.valueOf(tenantId);
    }
}

package com.forge.modules.workflow.framework;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流公共支撑工具（上帝类拆分第四批抽取的跨类公共件）
 *
 * @author forge-admin
 */
@Slf4j
public final class WorkflowSupport {

    private WorkflowSupport() {
    }
    /**
     * 候选人策略查询租户解析（评审 §4.5-P1#5 附属收敛）：租户由 ForgeTaskActorProvider
     * 以实例行快照注入上下文，此处读取；无来源时 warn 并返回 null（调用方返回空集走
     * 父类默认兜底，绝不猜租户）。单点实现替代四策略各自的 protected 副本。
     *
     * @param strategyName 调用方策略名（告警定位用）
     */
    public static Long resolveCandidateTenantId(String strategyName) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("候选人查询缺少租户上下文，返回空集: strategy={}", strategyName);
        }
        return tenantId;
    }


    /**
     * LIKE 通配符转义（ESCape 字符 '|'，配合 SQL 侧 ESCAPE '|' 使用）。
     * null/空白返回 null（调用方按"无过滤条件"跳过拼接）。
     * 原两份逐字副本位于 WfProcessInstanceServiceImpl/WfTaskServiceImpl（拆分收敛）。
     */
    public static String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("|", "||")
                .replace("%", "|%")
                .replace("_", "|_");
    }
}

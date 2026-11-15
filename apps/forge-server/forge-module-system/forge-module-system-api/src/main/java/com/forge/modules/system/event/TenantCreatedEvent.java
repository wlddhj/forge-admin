package com.forge.modules.system.event;

/**
 * 租户创建事件：租户落库并完成管理员/角色/套餐初始化后发布。
 *
 * <p>监听方（如插件模块的免费插件自动启用）经
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 消费，
 * 失败不回滚租户创建事务。
 *
 * @param tenantId 新租户编号
 * @author forge-admin
 */
public record TenantCreatedEvent(Long tenantId) {
}

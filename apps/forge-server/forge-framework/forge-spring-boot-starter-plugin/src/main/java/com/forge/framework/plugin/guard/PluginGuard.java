package com.forge.framework.plugin.guard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 插件请求闸——标注在插件模块的 Controller 类上，value 为所属插件编码。
 *
 * <p>切面在每次请求进入时校验「当前租户 × 插件」是否启用（经
 * {@link PluginStateResolver}，带 Redis 缓存），未启用直接拒绝，
 * 与菜单/权限码过滤共同构成停用后的三层收敛。
 *
 * @author forge-admin
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginGuard {

    /** 所属插件编码（对应 {@code PluginDescriptor#pluginCode()}） */
    String value();
}

package com.forge.framework.plugin.license;

import java.util.List;
import java.util.Map;

/**
 * License 载荷（canonical JSON 反序列化产物）。
 *
 * <p>时间字段为 ISO-8601 字符串（含时区偏移，如 2027-11-13T00:00:00+08:00），
 * 避免序列化时区歧义；由校验层解析。不含任何版本约束字段——License 只做
 * 「签名 + 租户 + 时间窗」三要素校验（02 设计 Q5 决策）。
 *
 * @param licenseKey  License 唯一编号（如 LIC-20261113-0001）
 * @param pluginCodes 授权插件编码集（一码多插件，至少一个）
 * @param tenantCode  绑定租户 code（全局唯一且稳定）
 * @param licensee    被授权方名称（公司主体，展示用）
 * @param issuedAt    签发时间
 * @param notBefore   生效起始（含）
 * @param notAfter    生效截止（含当日）
 * @param scopes      内置数据 scope（必须是各插件声明集的子集，可空）
 * @param extra       扩展位（未来按版本售卖等场景使用，本期不校验）
 * @author forge-admin
 */
public record LicensePayload(
        String licenseKey,
        List<String> pluginCodes,
        String tenantCode,
        String licensee,
        String issuedAt,
        String notBefore,
        String notAfter,
        List<String> scopes,
        Map<String, Object> extra) {
}

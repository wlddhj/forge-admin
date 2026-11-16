package com.forge.modules.contract.service;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 合同编号生成器：CT-{分类码}-{yyyyMMdd}-{4位序列}，Redis 自增按 租户+分类+日 隔离。
 * 允许跳号（生成后业务回滚），不允许重号。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class ContractNoGenerator {

    private static final String PREFIX = "CT";
    private static final String KEY_TEMPLATE = "contract:no:%d:%s:%s";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 键保留 48h（跨日新键，旧键过期清理） */
    private static final Duration KEY_TTL = Duration.ofHours(48);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成下一个合同编号（Redis DB 由应用统一配置，编号键不参与业务缓存清理）
     */
    public String next(String categoryCode) {
        Long tenantId = TenantContextHolder.getTenantId();
        long tid = tenantId == null ? 1L : tenantId;
        String date = LocalDate.now().format(DATE_FMT);
        String key = String.format(KEY_TEMPLATE, tid, categoryCode, date);
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            stringRedisTemplate.expire(key, KEY_TTL);
        }
        return String.format("%s-%s-%s-%04d", PREFIX, categoryCode, date, seq == null ? 1L : seq);
    }
}

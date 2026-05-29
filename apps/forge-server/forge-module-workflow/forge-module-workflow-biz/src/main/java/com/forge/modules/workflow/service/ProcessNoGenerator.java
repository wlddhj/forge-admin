package com.forge.modules.workflow.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 流程编号生成器
 * 规则：WL + yyMMdd + 4位顺序号，如 WL2605270001
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class ProcessNoGenerator {

    private static final String PREFIX = "WL";
    private static final String REDIS_KEY_PREFIX = "wf:process_no:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final StringRedisTemplate stringRedisTemplate;

    public String generateNo() {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String key = REDIS_KEY_PREFIX + dateStr;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        return PREFIX + dateStr + String.format("%04d", seq);
    }
}

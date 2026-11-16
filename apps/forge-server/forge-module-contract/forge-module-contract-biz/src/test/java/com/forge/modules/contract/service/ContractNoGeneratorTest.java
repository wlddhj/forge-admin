package com.forge.modules.contract.service;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 合同编号生成器单测（mock Redis）
 *
 * @author forge-admin
 */
@ExtendWith(MockitoExtension.class)
class ContractNoGeneratorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void nextFormatsSequenceWithTenantAndCategory() {
        TenantContextHolder.setTenantId(9L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(7L);

        String no = new ContractNoGenerator(stringRedisTemplate).next("LD");

        assertTrue(no.matches("CT-LD-\\d{8}-0007"), "编号格式: " + no);
        assertEquals("CT-LD-", no.substring(0, 6));
    }

    @Test
    void nextOverflowsFourDigitsWithoutCollision() {
        TenantContextHolder.setTenantId(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(12345L);

        String no = new ContractNoGenerator(stringRedisTemplate).next("XS");

        assertTrue(no.endsWith("-12345"), "超四位自然扩位不重号: " + no);
    }

    @Test
    void nextFallsBackTenantWhenContextMissing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        String no = new ContractNoGenerator(stringRedisTemplate).next("FW");

        assertTrue(no.startsWith("CT-FW-"), "无租户上下文回退默认租户仍可生成: " + no);
    }
}

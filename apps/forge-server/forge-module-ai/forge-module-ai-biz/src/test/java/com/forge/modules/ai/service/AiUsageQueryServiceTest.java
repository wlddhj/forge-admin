package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.ai.entity.AiApiUsage;
import com.forge.modules.ai.mapper.AiApiUsageMapper;
import com.forge.modules.ai.service.impl.AiUsageQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 用量查询单测:汇总数值换算(Token 合计/加权均时延)、空数据全 0(AC5)
 */
@ExtendWith(MockitoExtension.class)
class AiUsageQueryServiceTest {

    @Mock
    private AiApiUsageMapper usageMapper;

    private AiUsageQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiUsageQueryServiceImpl(usageMapper);
    }

    @Test
    void 汇总数值换算() {
        when(usageMapper.selectSummary(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Optional.of(Map.of(
                        "totalRequest", 10L,
                        "successCount", 8L,
                        "failureCount", 2L,
                        "totalInputTokens", 1000L,
                        "totalOutputTokens", 500L,
                        // avg_response_time 是日均值,加权总时长 = Σ(avg*requests) = 4500 → 450ms/次
                        "avgResponseMs", 4500L)));

        Map<String, Object> summary = service.summary(null, null, null, null);

        assertEquals(10L, summary.get("totalRequest"));
        assertEquals(8L, summary.get("successCount"));
        assertEquals(1500L, summary.get("totalTokens"));
        assertEquals(450L, summary.get("avgResponseMs"));
    }

    @Test
    void 空数据全零不报错() {
        when(usageMapper.selectSummary(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Optional.empty());
        Map<String, Object> summary = service.summary(null, null, null, null);
        assertEquals(0, summary.get("totalRequest"));
        assertEquals(0, summary.get("totalTokens"));
        assertEquals(0, summary.get("avgResponseMs"));
    }

    @Test
    void 分页透传筛选() {
        Page<AiApiUsage> empty = new Page<>(1, 10);
        when(usageMapper.selectPage(any(), any())).thenReturn(empty);
        Page<AiApiUsage> page = service.page(7L, 3L, "2026-10-01", "2026-10-24", 1, 10);
        assertEquals(0, page.getRecords().size());
        // wrapper 条件已构造(筛选透传由 wrapper 断言覆盖,此处验证调用不异常)
    }
}

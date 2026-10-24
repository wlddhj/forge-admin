package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.ai.entity.AiApiUsage;
import com.forge.modules.ai.mapper.AiApiUsageMapper;
import com.forge.modules.ai.service.AiUsageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 用量查询实现:分页明细 + SUM/COUNT 汇总(租户隔离经拦截器,表已含 tenant_id)
 */
@Service
@RequiredArgsConstructor
public class AiUsageQueryServiceImpl implements AiUsageQueryService {

    private final AiApiUsageMapper usageMapper;

    @Override
    public Page<AiApiUsage> page(Long userId, Long modelId, String startDate, String endDate,
                                 int pageNum, int pageSize) {
        return usageMapper.selectPage(new Page<>(pageNum, pageSize),
                buildWrapper(userId, modelId, startDate, endDate)
                        .orderByDesc(AiApiUsage::getUsageDate)
                        .orderByDesc(AiApiUsage::getId));
    }

    @Override
    public Map<String, Object> summary(Long userId, Long modelId, String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRequest", 0);
        result.put("successCount", 0);
        result.put("failureCount", 0);
        result.put("totalTokens", 0);
        result.put("avgResponseMs", 0);
        usageMapper.selectSummary(userId, modelId, parseDate(startDate), parseDate(endDate))
                .ifPresent(row -> {
                    result.put("totalRequest", row.getOrDefault("totalRequest", 0));
                    result.put("successCount", row.getOrDefault("successCount", 0));
                    result.put("failureCount", row.getOrDefault("failureCount", 0));
                    result.put("totalTokens",
                            ((Number) row.getOrDefault("totalInputTokens", 0)).longValue()
                                    + ((Number) row.getOrDefault("totalOutputTokens", 0)).longValue());
                    Object requests = row.get("totalRequest");
                    Object avgRaw = row.get("avgResponseMs");
                    long totalRequest = ((Number) requests).longValue();
                    double avg = totalRequest > 0 && avgRaw instanceof Number n
                            ? n.doubleValue() / totalRequest : 0;
                    result.put("avgResponseMs", Math.round(avg));
                });
        return result;
    }

    private LambdaQueryWrapper<AiApiUsage> buildWrapper(Long userId, Long modelId,
                                                        String startDate, String endDate) {
        LambdaQueryWrapper<AiApiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, AiApiUsage::getUserId, userId)
                .eq(modelId != null, AiApiUsage::getModelId, modelId)
                // 实体 usageDate 为 LocalDateTime(历史类型),日期边界换算为当日起止时刻
                .ge(startDate != null && !startDate.isBlank(), AiApiUsage::getUsageDate,
                        parseDate(startDate).atStartOfDay())
                .le(endDate != null && !endDate.isBlank(), AiApiUsage::getUsageDate,
                        parseDate(endDate).atTime(LocalTime.MAX));
        return wrapper;
    }

    private LocalDate parseDate(String date) {
        return date == null || date.isBlank() ? null : LocalDate.parse(date);
    }
}

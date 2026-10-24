package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.ai.entity.AiApiUsage;

import java.util.Map;

/**
 * AI 用量查询服务(查询侧,与记账侧 AiUsageRecorder 分离)
 */
public interface AiUsageQueryService {

    /**
     * 分页查询用量明细(筛选:用户/模型/日期范围,按日聚合行)
     */
    Page<AiApiUsage> page(Long userId, Long modelId, String startDate, String endDate,
                          int pageNum, int pageSize);

    /**
     * 汇总统计(同筛选):totalRequest/successCount/failureCount/totalTokens/avgResponseMs
     */
    Map<String, Object> summary(Long userId, Long modelId, String startDate, String endDate);
}

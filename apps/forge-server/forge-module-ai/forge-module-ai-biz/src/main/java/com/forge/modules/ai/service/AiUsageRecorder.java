package com.forge.modules.ai.service;

import com.forge.modules.ai.mapper.AiApiUsageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * LLM 用量记账：按（用户, 模型, 日）upsert 累加请求/成败/Token/平均时延。
 * 记账失败仅告警不外抛——用量统计不阻断业务主链路。
 */
@Slf4j
@Component
public class AiUsageRecorder {

    private final AiApiUsageMapper usageMapper;

    public AiUsageRecorder(AiApiUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    public void record(long userId, long modelId, boolean success,
                       int tokensIn, int tokensOut, int responseTimeMs) {
        try {
            usageMapper.upsertIncrement(userId, modelId, LocalDate.now(),
                    success ? 1 : 0, success ? 0 : 1,
                    Math.max(0, tokensIn), Math.max(0, tokensOut),
                    Math.max(0, responseTimeMs));
        } catch (Exception e) {
            log.warn("AI 用量记账失败 userId={} modelId={}: {}", userId, modelId, e.getMessage());
        }
    }
}

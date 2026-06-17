package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI API用量统计实体
 */
@Data
@TableName("ai_api_usage")
public class AiApiUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long modelId;

    private LocalDateTime usageDate;

    private Integer requestCount;

    private Integer successCount;

    private Integer failureCount;

    private Integer totalInputTokens;

    private Integer totalOutputTokens;

    private BigDecimal totalCost;

    private Integer avgResponseTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
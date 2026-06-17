package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型配置实体
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelName;

    private String modelCode;

    private String provider;

    private String apiEndpoint;

    private String apiKey;

    private Integer maxTokens;

    private BigDecimal temperature;

    private Integer contextWindow;

    private BigDecimal inputPrice;

    private BigDecimal outputPrice;

    private Integer isDefault;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
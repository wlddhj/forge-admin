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

    private String modelType;

    private String apiEndpoint;

    private String apiKey;

    private Integer maxTokens;

    private BigDecimal temperature;

    /**
     * 禁用思考模式(0:默认 1:请求注入 thinking.type=disabled，适用于 GLM-4.x 等思考类模型)
     */
    private Integer thinkingDisabled;

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
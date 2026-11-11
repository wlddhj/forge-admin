package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 消息记录实体
 */
@Data
@TableName("ai_chat_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private String conversationId;

    private String role;

    private String content;

    /**
     * 思考内容（思考模型 reasoning_content，仅 assistant 消息；历史回看展示）
     */
    private String reasoningContent;

    private Integer inputTokens;

    private Integer outputTokens;

    private BigDecimal cost;

    private Long modelId;

    private String parentMessageId;

    private String metadata;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;

    /** 租户ID(多租户部署由拦截器注入,单租户默认 1) */
    private Long tenantId;
}
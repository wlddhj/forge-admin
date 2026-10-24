package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 对话会话实体
 */
@Data
@TableName("ai_chat_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private Long userId;

    /** 租户ID(多租户部署由拦截器注入,单租户默认 1) */
    private Long tenantId;


    private Long modelId;

    private String title;

    private String summary;

    private Integer totalMessages;

    private Integer totalTokens;

    private BigDecimal totalCost;

    private Integer status;

    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
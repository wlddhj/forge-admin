package com.forge.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI对话请求
 */
@Data
public class ChatRequest {
    /**
     * 对话ID（可选，不传则创建新对话）
     */
    private Long conversationId;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 用户消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /**
     * 是否流式响应
     */
    private Boolean stream = false;

    /**
     * 温度参数（0-2）
     */
    private Double temperature;

    /**
     * 最大输出token数
     */
    private Integer maxTokens;

    /**
     * 关联的文档ID列表（可选）
     */
    private List<Long> documentIds;

    /**
     * 消息历史（包含上下文）
     */
    private List<MessageItem> messages;

    @Data
    public static class MessageItem {
        private String role;
        private String content;
    }
}
package com.forge.modules.ai.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话响应
 */
@Data
public class ConversationResponse {
    private Long id;
    private String title;
    private Long modelId;
    private String modelName;
    private String type;
    private Long documentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 消息列表
     */
    private List<MessageResponse> messages;

    /**
     * 消息响应（内部类）
     */
    @Data
    public static class MessageResponse {
        private Long id;
        private String role;  // user/assistant/system
        private String content;
        private Integer inputTokens;
        private Integer outputTokens;
        private LocalDateTime createTime;
    }
}
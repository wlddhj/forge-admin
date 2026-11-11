package com.forge.modules.ai.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息响应
 */
@Data
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private String role;  // user/assistant/system
    private String content;
    /**
     * 思考内容（思考模型 reasoning_content，仅 assistant 消息携带）
     */
    private String reasoningContent;
    private Integer inputTokens;
    private Integer outputTokens;
    private LocalDateTime createTime;
}

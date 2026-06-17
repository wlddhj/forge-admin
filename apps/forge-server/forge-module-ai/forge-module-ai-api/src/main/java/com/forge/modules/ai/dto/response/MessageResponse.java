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
    private Integer inputTokens;
    private Integer outputTokens;
    private LocalDateTime createTime;
}
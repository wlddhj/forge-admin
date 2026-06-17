package com.forge.modules.ai.dto.request;

import lombok.Data;

/**
 * 创建对话请求
 */
@Data
public class CreateConversationRequest {
    /**
     * 对话标题
     */
    private String title;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 对话类型（chat/summary/qa）
     */
    private String type = "chat";

    /**
     * 关联的文档ID（可选）
     */
    private Long documentId;
}
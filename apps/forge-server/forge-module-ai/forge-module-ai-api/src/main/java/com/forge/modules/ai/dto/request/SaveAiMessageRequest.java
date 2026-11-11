package com.forge.modules.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存 AI 回复消息请求（流式结束后前端回传落库）
 *
 * @author standadmin
 */
@Data
public class SaveAiMessageRequest {

    @Schema(description = "回复正文")
    @NotNull(message = "content 不能为空")
    private String content;

    @Schema(description = "思考内容（思考模型 reasoning_content，可为空）")
    private String reasoningContent;
}

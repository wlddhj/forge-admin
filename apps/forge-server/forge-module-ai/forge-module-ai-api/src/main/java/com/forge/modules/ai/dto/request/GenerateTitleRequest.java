package com.forge.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 生成会话标题请求
 */
@Data
public class GenerateTitleRequest {

    /**
     * 会话首条用户消息（生成标题的依据）
     */
    @NotBlank(message = "消息内容不能为空")
    private String content;
}

package com.forge.modules.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Python AI服务返回的原始响应格式
 */
@Data
public class PythonChatResponse {
    private String id;
    private String provider;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    private String created;

    @Data
    public static class Choice {
        private Integer index;
        private Message message;
        private String finishReason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
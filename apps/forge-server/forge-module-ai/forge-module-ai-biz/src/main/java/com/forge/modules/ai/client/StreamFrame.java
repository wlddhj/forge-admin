package com.forge.modules.ai.client;

/**
 * 原生 OpenAI 兼容流帧：reasoning/content 增量与携带 usage 的结束帧
 *
 * @author standadmin
 */
public record StreamFrame(Type type, String text, Long promptTokens, Long completionTokens) {

    public enum Type {
        /** 思考增量（GLM delta.reasoning_content，非思考模型不产生） */
        REASONING,
        /** 正文增量 */
        CONTENT,
        /** 流结束（usage 可能为 null：上游未返回或未请求 stream_options） */
        DONE
    }

    public static StreamFrame reasoning(String text) {
        return new StreamFrame(Type.REASONING, text, null, null);
    }

    public static StreamFrame content(String text) {
        return new StreamFrame(Type.CONTENT, text, null, null);
    }

    public static StreamFrame done(Long promptTokens, Long completionTokens) {
        return new StreamFrame(Type.DONE, null, promptTokens, completionTokens);
    }
}

package com.forge.modules.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 模型提供商枚举
 */
@Getter
@AllArgsConstructor
public enum ModelProvider {

    QWEN("qwen", "通义千问"),
    ERNIE("ernie", "文心一言"),
    DEEPSEEK("deepseek", "DeepSeek"),
    GLM("glm", "智谱GLM");

    private final String code;
    private final String name;
}
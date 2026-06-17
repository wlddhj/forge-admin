package com.forge.modules.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档处理状态枚举
 */
@Getter
@AllArgsConstructor
public enum DocumentStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");

    private final Integer code;
    private final String name;
}
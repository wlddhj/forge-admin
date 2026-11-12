package com.forge.modules.workflow.dto.callback;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务回调配置响应
 * <p>
 * secret 加密存储、不回显明文，仅以 hasSecret 标记是否已配置。
 *
 * @author forge-admin
 */
@Data
public class CallbackResponse {

    private Long id;
    private String name;
    private String url;
    /** 签名密钥是否已配置（出参不回显明文） */
    private Boolean hasSecret;
    private List<String> events;
    private String processKey;
    private Integer includeVariables;
    private Integer enabled;
    private Integer timeoutMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

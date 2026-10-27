package com.forge.modules.workflow.dto.callback;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务回调配置响应
 *
 * @author forge-admin
 */
@Data
public class CallbackResponse {

    private Long id;
    private String name;
    private String url;
    private String secret;
    private List<String> events;
    private String processKey;
    private Integer includeVariables;
    private Integer enabled;
    private Integer timeoutMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

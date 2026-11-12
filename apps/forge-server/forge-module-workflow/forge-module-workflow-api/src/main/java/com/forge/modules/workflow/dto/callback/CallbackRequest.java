package com.forge.modules.workflow.dto.callback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 业务回调配置新增/编辑请求
 *
 * @author forge-admin
 */
@Data
public class CallbackRequest {

    private Long id;

    @NotBlank(message = "回调名称不能为空")
    private String name;

    @NotBlank(message = "回调地址不能为空")
    @Pattern(regexp = "^https?://.+", message = "回调地址必须是 http(s):// 开头")
    private String url;

    /**
     * 签名密钥（可选，空则不签名）
     */
    private String secret;

    /**
     * 订阅事件列表（taskCreated/taskCompleted/instanceFinished）
     */
    private List<String> events;

    /**
     * 流程标识过滤（空=全部流程）
     */
    private String processKey;

    /**
     * 是否随实例终态事件携带流程变量（0/1）
     */
    private Integer includeVariables;

    /**
     * 是否启用（0/1）
     */
    private Integer enabled;

    /**
     * HTTP 超时（毫秒，默认 5000；服务端同步钳制，超过 30s 按 30s 投递）
     */
    @Max(value = 30000, message = "HTTP 超时不能超过 30000ms")
    private Integer timeoutMs;
}

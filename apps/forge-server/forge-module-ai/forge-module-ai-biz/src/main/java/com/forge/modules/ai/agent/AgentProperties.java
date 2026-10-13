package com.forge.modules.ai.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 编排配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "forge.ai.agent")
public class AgentProperties {

    /**
     * HTTP 工具允许访问的域名白名单（空=禁用 HTTP 工具）
     */
    private List<String> allowedHosts = List.of();

    /**
     * SQL 查询工具是否启用
     */
    private boolean sqlToolEnabled = true;

    /**
     * SQL 工具返回行数上限
     */
    private int sqlMaxRows = 200;

    /**
     * HTTP/工具输出截断长度
     */
    private int toolOutputLimit = 4000;
}

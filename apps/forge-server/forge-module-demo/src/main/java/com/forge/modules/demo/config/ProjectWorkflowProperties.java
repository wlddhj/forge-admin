package com.forge.modules.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 项目立项工作流对接配置
 *
 * @author forge-admin
 */
@Data
@Component
@ConfigurationProperties(prefix = "forge.demo")
public class ProjectWorkflowProperties {

    /**
     * 项目立项流程绑定的流程标识（模型管理中的流程 Key）。
     * 按解析最新已部署版本，重新部署后自动跟随，无需改配置。
     */
    private String projectProcessKey = "project_approval";
}

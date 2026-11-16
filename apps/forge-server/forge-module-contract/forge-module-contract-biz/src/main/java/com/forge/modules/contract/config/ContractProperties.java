package com.forge.modules.contract.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 合同模块配置
 *
 * @author forge-admin
 */
@Data
@Component
@ConfigurationProperties(prefix = "forge.contract")
public class ContractProperties {

    /** 审批流程 processKey（重新部署自动跟随最新版本） */
    private String processKey = "contract_approval";

    /** 启动时自动部署默认审批模型（仅当该 key 无已部署定义时） */
    private boolean autoDeployModel = true;
}

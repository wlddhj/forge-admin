package com.forge.modules.contract.listener;

import com.forge.common.exception.BusinessException;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.contract.config.ContractProperties;
import com.forge.modules.workflow.dto.definition.ProcessDeployRequest;
import com.forge.modules.workflow.service.WfProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认审批模型供给器：processKey 无已部署定义时，按当前租户上下文部署最小默认模型
 * （start → 合同审批(超管) → 结束）。启动预置与提交时懒部署（按租户）共用本组件。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractModelProvisioner {

    /** 最小默认模型：发起人 → 合同审批（超管审批，或签模式 3） */
    static final String DEFAULT_MODEL_JSON = """
            {
              "name": "合同审批",
              "key": "contract_approval",
              "nodeConfig": {
                "nodeName": "发起人",
                "nodeKey": "start",
                "type": 0,
                "childNode": {
                  "nodeName": "合同审批",
                  "nodeKey": "cap1",
                  "type": 1,
                  "setType": 1,
                  "nodeAssigneeList": [{"id": "1", "name": "超级管理员"}],
                  "examineMode": 3
                }
              }
            }
            """;

    private final WfProcessDefinitionService definitionService;
    private final ContractProperties properties;

    /**
     * 确保当前租户存在 contract_approval 定义（缺失且允许时部署默认模型）
     */
    public void ensureModelExists() {
        try {
            definitionService.getLatestDefinitionIdByKey(properties.getProcessKey());
        } catch (BusinessException e) {
            if (!properties.isAutoDeployModel() || e.getCode() == null || e.getCode() != 404) {
                throw e;
            }
            deployDefault();
        }
    }

    /**
     * 解析当前租户最新定义ID（须先 ensureModelExists）
     */
    public String resolveDefinitionId() {
        return definitionService.getLatestDefinitionIdByKey(properties.getProcessKey());
    }

    private void deployDefault() {
        ProcessDeployRequest request = new ProcessDeployRequest();
        request.setName("合同审批");
        request.setKey(properties.getProcessKey());
        request.setDescription("合同模块默认审批模型（自动部署，可在流程设计器替换）");
        request.setModelJson(DEFAULT_MODEL_JSON);
        definitionService.deploy(request);
        log.info("合同默认审批模型已自动部署: processKey={}, tenant={}",
                properties.getProcessKey(), TenantContextHolder.getTenantId());
    }
}

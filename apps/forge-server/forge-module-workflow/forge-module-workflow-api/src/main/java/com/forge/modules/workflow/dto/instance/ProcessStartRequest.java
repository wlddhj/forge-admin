package com.forge.modules.workflow.dto.instance;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 发起流程请求
 *
 * @author forge-admin
 */
@Data
public class ProcessStartRequest {

    /**
     * 流程定义ID
     */
    @NotBlank(message = "流程定义ID不能为空")
    private String processDefinitionId;

    /**
     * 业务Key
     */
    private String businessKey;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;

    /**
     * 提交意见
     */
    private String comment;

    /**
     * 发起人自选审批人
     * key: 节点定义Key（taskDefKey）
     * value: 审批人ID列表
     */
    private Map<String, String[]> startUserSelectActors;
}

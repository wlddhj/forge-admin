package com.forge.modules.workflow.dto.instance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /**
     * 流程优先级（0-普通, 1-高优先级）
     * 默认为0
     */
    private Integer priority = 0;

    /**
     * 紧急程度（0 常规 1 重要不紧急 2 紧急不重要 3 紧急且重要，flowlong 1.2.7）
     */
    private Integer urgent = 0;

    /**
     * 显式流程标题：非空时覆盖模型标题规则的渲染结果（业务模块特殊场景）
     */
    @Size(max = 200, message = "流程标题不能超过200个字符")
    private String title;
}

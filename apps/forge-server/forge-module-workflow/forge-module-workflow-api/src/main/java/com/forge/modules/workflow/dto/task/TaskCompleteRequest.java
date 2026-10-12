package com.forge.modules.workflow.dto.task;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务完成请求
 *
 * @author forge-admin
 */
@Data
public class TaskCompleteRequest {

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 附件ID列表
     */
    private List<Long> attachmentIds;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;
}

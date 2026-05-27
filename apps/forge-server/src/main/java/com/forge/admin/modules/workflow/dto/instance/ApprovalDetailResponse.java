package com.forge.admin.modules.workflow.dto.instance;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批详情响应
 *
 * @author forge
 */
@Data
public class ApprovalDetailResponse {

    private String processInstanceId;
    private String processInstanceName;
    private String processDefinitionId;
    private String category;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long startUserId;
    private String startUserName;
    private String bpmnXml;
    private List<ApprovalNode> nodes;

    @Data
    public static class ApprovalNode {
        private String activityId;
        private String activityName;
        private String activityType;
        private Integer status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<ApprovalTask> tasks;
    }

    @Data
    public static class ApprovalTask {
        private String taskId;
        private Long userId;
        private String userName;
        private Integer status;
        private String comment;
        private LocalDateTime createTime;
        private LocalDateTime endTime;
    }
}

package com.forge.modules.workflow.dto.copy;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CopyResponse {

    private Long id;
    private Long startUserId;
    private String startUserName;
    private String processInstanceName;
    private String processInstanceId;
    private String processNo;
    private String category;
    private String activityId;
    private String activityName;
    private String taskId;
    private Long userId;
    private String userName;
    private String reason;
    private LocalDateTime createTime;
}

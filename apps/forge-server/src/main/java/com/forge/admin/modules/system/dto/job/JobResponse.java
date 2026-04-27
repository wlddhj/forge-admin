package com.forge.admin.modules.system.dto.job;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class JobResponse {
    private Long id;
    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String cronExpression;
    private Integer status;
    private Integer concurrent;
    private String remark;
    private Integer timeout;
    private Integer retryCount;
    private Integer retryInterval;
    private Map<String, Object> notifyConfig;
    private Map<String, Object> jobParams;
    private LocalDateTime lastExecuteAt;
    private String lastExecuteStatus;
    private Long lastExecuteDuration;
    private Integer totalExecuteCount;
    private Integer successCount;
    private Integer failureCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String nextValidTime;
}

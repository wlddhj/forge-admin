package com.forge.admin.modules.system.dto.job;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Data
public class JobRequest {
    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String jobName;

    private String jobGroup = "DEFAULT";

    @NotBlank(message = "调用目标不能为空")
    private String invokeTarget;

    @NotBlank(message = "cron表达式不能为空")
    private String cronExpression;

    private Integer status = 1;

    private Integer concurrent = 0;

    private String remark;

    private Integer timeout;

    private Integer retryCount;

    private Integer retryInterval;

    private Map<String, Object> notifyConfig;

    private Map<String, Object> jobParams;
}

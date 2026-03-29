package com.forge.admin.modules.system.dto.job;

import lombok.Data;
import java.time.LocalDateTime;

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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String nextValidTime;
}

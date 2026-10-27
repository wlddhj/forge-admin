package com.forge.modules.workflow.dto.callback;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务回调投递记录响应
 *
 * @author forge-admin
 */
@Data
public class CallbackRecordResponse {

    private Long id;
    private Long callbackId;
    private String callbackName;
    private String eventType;
    private String processKey;
    private String businessKey;
    private Long instanceId;
    private Long taskId;
    private String nodeKey;
    private String payload;
    private Integer httpStatus;
    private String responseBody;
    /**
     * SUCCESS / FAILED
     */
    private String status;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime createTime;
}

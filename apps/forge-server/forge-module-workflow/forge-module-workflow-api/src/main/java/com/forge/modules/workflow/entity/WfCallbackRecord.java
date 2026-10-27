package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务回调投递记录实体（每次投递终态一条，含重试汇总）
 *
 * @author forge-admin
 */
@Data
@TableName("wf_callback_record")
public class WfCallbackRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 回调配置ID
     */
    private Long callbackId;

    /**
     * 回调名称（快照）
     */
    private String callbackName;

    /**
     * 事件类型（taskCreated/taskCompleted/instanceFinished）
     */
    private String eventType;

    private String processKey;

    private String businessKey;

    private Long instanceId;

    private Long taskId;

    private String nodeKey;

    /**
     * 请求体JSON
     */
    private String payload;

    /**
     * 最后响应HTTP状态码
     */
    private Integer httpStatus;

    /**
     * 最后响应体（截断）
     */
    private String responseBody;

    /**
     * 投递状态（SUCCESS/FAILED）
     */
    private String status;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 失败原因
     */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

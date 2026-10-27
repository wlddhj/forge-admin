package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务回调配置实体（业务系统订阅工作流事件）
 *
 * @author forge-admin
 */
@Data
@TableName("wf_business_callback")
public class WfBusinessCallback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 回调名称
     */
    private String name;

    /**
     * 回调地址（http/https）
     */
    private String url;

    /**
     * 签名密钥（HMAC-SHA256，空则不签名）
     */
    private String secret;

    /**
     * 订阅事件CSV：taskCreated,taskCompleted,instanceFinished
     */
    private String events;

    /**
     * 流程标识过滤（空=全部流程）
     */
    private String processKey;

    /**
     * 是否随实例终态事件携带流程变量（0/1）
     */
    private Integer includeVariables;

    /**
     * 是否启用（0/1）
     */
    private Integer enabled;

    /**
     * HTTP 超时（毫秒）
     */
    private Integer timeoutMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

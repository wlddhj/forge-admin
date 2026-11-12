package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.forge.framework.mybatis.handler.EncryptTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务回调配置实体（业务系统订阅工作流事件）
 * <p>
 * secret 经 EncryptTypeHandler AES-256-GCM 加密存储、读取自动解密；
 * 管理接口出参不回显明文（仅 hasSecret），沿 wf_im_channel_config 先例。
 *
 * @author forge-admin
 */
@Data
@TableName(value = "wf_business_callback", autoResultMap = true)
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
     * 签名密钥（HMAC-SHA256，空则不签名；AES-256-GCM 加密存储，读取自动解密）
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
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

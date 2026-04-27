package com.forge.admin.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 定时任务实体
 */
@Data
@TableName(value = "sys_job", autoResultMap = true)
public class SysJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    private String jobName;

    /** 任务分组 */
    private String jobGroup;

    /** 调用目标 */
    private String invokeTarget;

    /** cron表达式 */
    private String cronExpression;

    /** 任务状态(0:暂停 1:正常) */
    private Integer status;

    /** 是否并发执行(0:禁止 1:允许) */
    private Integer concurrent;

    /** 备注 */
    private String remark;

    /** 超时时间(秒) */
    private Integer timeout;

    /** 失败重试次数 */
    private Integer retryCount;

    /** 重试间隔(秒) */
    private Integer retryInterval;

    /** 通知配置 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> notifyConfig;

    /** 任务参数 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> jobParams;

    /** 最后执行时间 */
    private LocalDateTime lastExecuteAt;

    /** 最后执行状态(SUCCESS/FAIL/TIMEOUT) */
    private String lastExecuteStatus;

    /** 最后执行耗时(毫秒) */
    private Long lastExecuteDuration;

    /** 总执行次数 */
    private Integer totalExecuteCount;

    /** 成功次数 */
    private Integer successCount;

    /** 失败次数 */
    private Integer failureCount;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

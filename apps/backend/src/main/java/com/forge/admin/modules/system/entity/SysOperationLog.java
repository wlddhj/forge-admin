package com.forge.admin.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作标题
     */
    private String title;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 请求方式
     */
    private String requestMethod;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 操作IP
     */
    private String operateIp;

    /**
     * 操作地点
     */
    private String operateLocation;

    /**
     * 请求参数
     */
    private String requestParam;

    /**
     * 响应结果
     */
    private String jsonResult;

    /**
     * 状态(0:失败 1:成功)
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 耗时(毫秒)
     */
    private Long costTime;
}

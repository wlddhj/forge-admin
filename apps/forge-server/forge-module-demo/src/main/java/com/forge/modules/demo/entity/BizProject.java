package com.forge.modules.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目立项实体（业务模块接入工作流示例）
 *
 * @author forge-admin
 */
@Data
@TableName("demo_biz_project")
public class BizProject {

    /**
     * 审批中：已提交、流程在途
     */
    public static final String STATUS_APPROVING = "APPROVING";
    /**
     * 已通过：实例 end/autoComplete/timeoutComplete 终态
     */
    public static final String STATUS_APPROVED = "APPROVED";
    /**
     * 已驳回：实例 rejectComplete/autoReject 终态
     */
    public static final String STATUS_REJECTED = "REJECTED";
    /**
     * 已撤销：实例 revokeComplete 终态
     */
    public static final String STATUS_CANCELED = "CANCELED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目金额
     */
    private BigDecimal totalAmount;

    /**
     * 责任人
     */
    private String bizUser;

    /**
     * 责任部门
     */
    private String deptName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 流程实例ID（发起后由 businessKey 查回回写）
     */
    private Long processInstanceId;

    /**
     * 流程编号（发起回写，同实例 processNo）
     */
    private String processNo;

    /**
     * 审批状态 APPROVING/APPROVED/REJECTED/CANCELED，仅由事件回写变更
     */
    private String approveStatus;

    /**
     * 审批完成时间（终态事件回写，取实例 endTime）
     */
    private LocalDateTime approveTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

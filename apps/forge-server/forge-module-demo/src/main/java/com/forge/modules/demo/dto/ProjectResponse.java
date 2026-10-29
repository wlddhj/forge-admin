package com.forge.modules.demo.dto;

import com.forge.modules.demo.entity.BizProject;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目立项响应
 *
 * @author forge-admin
 */
@Data
public class ProjectResponse {

    /**
     * 业务ID（全局 Jackson 将 Long 序列化为字符串）
     */
    private Long id;

    private String projectName;

    private BigDecimal totalAmount;

    private String bizUser;

    private String deptName;

    private String remark;

    /**
     * 流程实例ID，可跳转 /workflow/instance/detail?id= 查看审批进度
     */
    private Long processInstanceId;

    /**
     * 流程编号（发起回写）
     */
    private String processNo;

    /**
     * 审批状态 APPROVING/APPROVED/REJECTED/CANCELED
     */
    private String approveStatus;

    /**
     * 审批状态名称
     */
    private String approveStatusName;

    /**
     * 审批完成时间（终态事件回写）
     */
    private LocalDateTime approveTime;

    private Long createBy;

    private LocalDateTime createTime;

    /**
     * 是否允许当前上下文撤销（APPROVING 且有实例），最终以后端校验为准
     */
    private Boolean cancelable;

    public static ProjectResponse from(BizProject project) {
        ProjectResponse response = new ProjectResponse();
        response.setId(project.getId());
        response.setProjectName(project.getProjectName());
        response.setTotalAmount(project.getTotalAmount());
        response.setBizUser(project.getBizUser());
        response.setDeptName(project.getDeptName());
        response.setRemark(project.getRemark());
        response.setProcessInstanceId(project.getProcessInstanceId());
        response.setProcessNo(project.getProcessNo());
        response.setApproveStatus(project.getApproveStatus());
        response.setApproveStatusName(approveStatusName(project.getApproveStatus()));
        response.setApproveTime(project.getApproveTime());
        response.setCreateBy(project.getCreateBy());
        response.setCreateTime(project.getCreateTime());
        response.setCancelable(BizProject.STATUS_APPROVING.equals(project.getApproveStatus())
                && project.getProcessInstanceId() != null);
        return response;
    }

    public static String approveStatusName(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case BizProject.STATUS_APPROVING -> "审批中";
            case BizProject.STATUS_APPROVED -> "已通过";
            case BizProject.STATUS_REJECTED -> "已驳回";
            case BizProject.STATUS_CANCELED -> "已撤销";
            default -> status;
        };
    }
}

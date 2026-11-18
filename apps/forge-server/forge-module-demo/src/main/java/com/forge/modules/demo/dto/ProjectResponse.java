package com.forge.modules.demo.dto;

import com.forge.modules.demo.entity.BizProject;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.TransPojo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目立项响应
 *
 * @author forge-admin
 */
@Data
public class ProjectResponse implements TransPojo {

    /**
     * 审批状态字典类型（码值对齐 demo_biz_project.approve_status，种子见 upgrade-20261118-project-status-dict.sql）。
     * 定义在 DTO：{@code @Trans(key=)} 要求编译期常量，且消除 DTO 对 Service 的反向引用
     */
    public static final String STATUS_DICT_TYPE = "demo_project_status";

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
    @Trans(type = TransType.DICTIONARY, key = STATUS_DICT_TYPE, ref = "approveStatusDictLabel")
    private String approveStatus;

    /**
     * 审批状态名称（Service 手动组装：DictLabelUtils 页级 resolveMap，未命中回退码值本身）
     */
    private String approveStatusName;

    /**
     * 审批状态 label（easy-trans DICTIONARY 注解自动翻译示范字段，与 approveStatusName 手动组装并存对照：
     * 响应阶段由全局翻译填充，字典写路径刷新钩子保证字典变更后即时生效；未命中（字典未配置/全停用）返回 null）
     */
    private String approveStatusDictLabel;

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
        response.setApproveTime(project.getApproveTime());
        response.setCreateBy(project.getCreateBy());
        response.setCreateTime(project.getCreateTime());
        response.setCancelable(BizProject.STATUS_APPROVING.equals(project.getApproveStatus())
                && project.getProcessInstanceId() != null);
        return response;
    }
}

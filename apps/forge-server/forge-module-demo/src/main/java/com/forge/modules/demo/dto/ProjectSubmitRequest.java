package com.forge.modules.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提交立项请求（字段与流程表单约定的变量一一对应）
 *
 * @author forge-admin
 */
@Data
public class ProjectSubmitRequest {

    /**
     * 项目名称（流程变量 projectName）
     */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称不能超过128个字符")
    private String projectName;

    /**
     * 项目金额（流程变量 totalAmount，条件分支可引用如 #totalAmount > 10000）
     */
    @DecimalMin(value = "0", message = "项目金额不能为负数")
    private BigDecimal totalAmount;

    /**
     * 责任人（流程变量 bizUser）
     */
    @Size(max = 64, message = "责任人不能超过64个字符")
    private String bizUser;

    /**
     * 责任部门（流程变量 deptName）
     */
    @Size(max = 64, message = "责任部门不能超过64个字符")
    private String deptName;

    /**
     * 备注（流程变量 remark）
     */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}

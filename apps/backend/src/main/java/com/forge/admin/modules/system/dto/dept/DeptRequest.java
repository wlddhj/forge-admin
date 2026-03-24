package com.forge.admin.modules.system.dto.dept;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeptRequest {
    private Long id;
    @NotBlank(message = "部门名称不能为空")
    private String deptName;
    private Long parentId;
    private String leader;
    private String email;
    private String phone;
    private Integer status;
    private Integer sortOrder;
}

package com.forge.admin.modules.system.dto.role;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RoleRequest {
    private Long id;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
    private String description;
    private Integer isFixed;
    private Integer status;
    /**
     * 数据范围（1:全部 2:自定义 3:本部门 4:本部门及以下 5:仅本人）
     */
    private String dataScope;
    private Integer sortOrder;
    /**
     * 自定义数据权限-部门ID列表
     */
    private List<Long> deptIds;
}

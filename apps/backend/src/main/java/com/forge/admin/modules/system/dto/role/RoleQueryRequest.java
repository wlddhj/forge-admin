package com.forge.admin.modules.system.dto.role;

import lombok.Data;

@Data
public class RoleQueryRequest {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String roleName;
    private String roleCode;
    private Integer status;
}

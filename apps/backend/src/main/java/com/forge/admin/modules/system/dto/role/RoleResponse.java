package com.forge.admin.modules.system.dto.role;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoleResponse {
    private Long id;
    private String roleName;
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
    private LocalDateTime createTime;
}

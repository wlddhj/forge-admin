package com.forge.admin.modules.system.dto.dept;

import lombok.Data;
import java.util.List;

@Data
public class DeptTreeResponse {
    private Long id;
    private String deptName;
    private Long parentId;
    private String leader;
    private String email;
    private String phone;
    private Integer status;
    private Integer sortOrder;
    private List<DeptTreeResponse> children;
}

package com.forge.admin.modules.system.dto.dept;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeptResponse {
    private Long id;
    private String deptName;
    private Long parentId;
    private String ancestors;
    private String leader;
    private String email;
    private String phone;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createTime;
}

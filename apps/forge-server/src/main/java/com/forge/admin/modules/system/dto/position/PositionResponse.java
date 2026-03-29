package com.forge.admin.modules.system.dto.position;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位响应
 */
@Data
public class PositionResponse {
    private Long id;
    private String positionName;
    private String positionCode;
    private Long deptId;
    private String deptName;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
}

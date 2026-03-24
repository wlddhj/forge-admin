package com.forge.admin.modules.system.dto.position;

import lombok.Data;

/**
 * 岗位查询请求
 */
@Data
public class PositionQueryRequest {
    private String positionName;
    private String positionCode;
    private Integer status;
    private Long deptId;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}

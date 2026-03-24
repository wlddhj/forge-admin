package com.forge.admin.modules.system.dto.position;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 岗位请求
 */
@Data
public class PositionRequest {
    private Long id;

    @NotBlank(message = "岗位名称不能为空")
    private String positionName;

    @NotBlank(message = "岗位编码不能为空")
    private String positionCode;

    private Long deptId;
    private Integer sortOrder = 0;
    private Integer status = 1;
}

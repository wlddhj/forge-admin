package com.forge.modules.system.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 租户分页查询请求
 *
 * @author standadmin
 */
@Data
@Schema(description = "租户查询请求")
public class TenantQueryRequest {

    @Schema(description = "页码", example = "1")
    private Long pageNum = 1L;

    @Schema(description = "每页数量", example = "10")
    private Long pageSize = 10L;

    @Schema(description = "租户名称（模糊查询）")
    private String name;

    @Schema(description = "租户标识（模糊查询）")
    private String code;

    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;

    @Schema(description = "套餐ID")
    private Long packageId;
}

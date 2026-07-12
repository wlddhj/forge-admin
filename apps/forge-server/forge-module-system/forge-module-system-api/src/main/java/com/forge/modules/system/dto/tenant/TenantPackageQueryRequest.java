package com.forge.modules.system.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 租户套餐分页查询请求
 *
 * @author standadmin
 */
@Data
@Schema(description = "租户套餐查询请求")
public class TenantPackageQueryRequest {

    @Schema(description = "页码", example = "1")
    private Long pageNum = 1L;

    @Schema(description = "每页数量", example = "10")
    private Long pageSize = 10L;

    @Schema(description = "套餐名称（模糊查询）")
    private String name;

    @Schema(description = "套餐编码（模糊查询）")
    private String code;

    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;
}

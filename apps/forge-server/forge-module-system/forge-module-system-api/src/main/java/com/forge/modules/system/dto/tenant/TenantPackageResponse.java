package com.forge.modules.system.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户套餐响应
 *
 * @author standadmin
 */
@Data
@Schema(description = "租户套餐响应")
public class TenantPackageResponse {

    @Schema(description = "套餐ID")
    private Long id;

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "套餐编码")
    private String code;

    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联菜单ID列表")
    private List<Long> menuIds;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

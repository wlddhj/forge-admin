package com.forge.modules.system.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 租户套餐新增/修改请求
 *
 * @author standadmin
 */
@Data
@Schema(description = "租户套餐请求")
public class TenantPackageRequest {

    @Schema(description = "套餐ID（修改时必传）")
    private Long id;

    @Schema(description = "套餐名称")
    @NotBlank(message = "套餐名称不能为空")
    private String name;

    @Schema(description = "套餐编码")
    @NotBlank(message = "套餐编码不能为空")
    private String code;

    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联菜单ID列表")
    private List<Long> menuIds;
}

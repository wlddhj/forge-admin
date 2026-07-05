package com.forge.modules.screen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 大屏新增/修改请求
 *
 * @author standadmin
 */
@Data
@Schema(description = "大屏新增/修改请求")
public class ScreenRequest {

    @Schema(description = "ID（修改时必传）")
    private Long id;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "路由编码")
    private String code;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "显示名")
    private String name;

    @Size(max = 512)
    @Schema(description = "说明")
    private String description;

    @Schema(description = "主题")
    private String theme = "dark-tech";

    @Schema(description = "备注")
    private String remark;
}

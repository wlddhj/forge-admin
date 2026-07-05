package com.forge.modules.screen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 大屏分页查询
 *
 * @author standadmin
 */
@Data
@Schema(description = "大屏分页查询")
public class ScreenPageRequest {
    @Schema(description = "页码")
    private Integer pageNum = 1;

    @Schema(description = "页大小")
    private Integer pageSize = 10;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "状态 0=草稿 1=已发布")
    private Integer status;
}

package com.forge.modules.screen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 大屏复制请求
 *
 * @author standadmin
 */
@Data
@Schema(description = "大屏复制请求")
public class ScreenCopyRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "新大屏 code")
    private String newCode;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "新大屏名称")
    private String newName;
}

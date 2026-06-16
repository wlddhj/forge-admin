package com.forge.modules.system.dto.app;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 账号注销请求
 *
 * @author forge
 */
@Data
@Schema(description = "账号注销请求")
public class DeactivateRequest {
    /**
     * 是否确认注销
     */
    @Schema(description = "是否确认注销", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "确认标志不能为空")
    @AssertTrue(message = "必须确认注销")
    private Boolean confirm;
}
package com.forge.modules.system.dto.app;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 短信验证码请求
 *
 * @author forge
 */
@Data
@Schema(description = "短信验证码请求")
public class SmsCodeRequest {
    /**
     * 手机号
     */
    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 场景：BIND_PHONE, CHANGE_PHONE（预留）
     */
    @Schema(description = "场景：BIND_PHONE, CHANGE_PHONE（预留）")
    private String scene;
}
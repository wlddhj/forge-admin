package com.forge.modules.system.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码响应
 *
 * @author standadmin
 */
@Schema(description = "验证码响应")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse {

    @Schema(description = "验证码ID", example = "abc123")
    private String captchaId;

    @Schema(description = "验证码图片（Base64编码）", example = "data:image/png;base64,iVBORw0KGgo...")
    private String captchaImage;
}
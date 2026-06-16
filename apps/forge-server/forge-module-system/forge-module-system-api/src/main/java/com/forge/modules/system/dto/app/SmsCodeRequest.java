package com.forge.modules.system.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 短信验证码请求
 *
 * @author forge
 */
@Data
public class SmsCodeRequest {
    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 场景：BIND_PHONE, CHANGE_PHONE（预留）
     */
    private String scene;
}
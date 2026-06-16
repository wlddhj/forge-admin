package com.forge.modules.system.service.sms;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

/**
 * 短信服务接口
 *
 * @author forge
 */
public interface SmsService {

    /**
     * 发送短信验证码
     *
     * @param phone 手机号
     * @param code 验证码
     */
    @Operation(summary = "发送短信验证码")
    void send(
            @Parameter(description = "手机号") String phone,
            @Parameter(description = "验证码") String code);
}
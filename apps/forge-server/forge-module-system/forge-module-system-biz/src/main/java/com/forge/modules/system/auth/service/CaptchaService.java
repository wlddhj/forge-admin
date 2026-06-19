package com.forge.modules.system.auth.service;

import com.forge.modules.system.auth.dto.CaptchaResponse;

/**
 * 验证码服务
 *
 * @author standadmin
 */
public interface CaptchaService {

    /**
     * 生成验证码
     *
     * @return 验证码响应（包含 captchaId 和图片 Base64）
     */
    CaptchaResponse generate();

    /**
     * 校验验证码
     *
     * @param captchaId 验证码ID
     * @param code      用户输入的验证码
     * @return true 表示校验通过
     */
    boolean validate(String captchaId, String code);

    /**
     * 删除验证码（校验成功后删除）
     *
     * @param captchaId 验证码ID
     */
    void delete(String captchaId);
}
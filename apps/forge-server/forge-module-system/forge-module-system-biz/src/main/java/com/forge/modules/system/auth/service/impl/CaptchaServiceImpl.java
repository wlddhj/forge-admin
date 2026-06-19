package com.forge.modules.system.auth.service.impl;

import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.lang.UUID;
import com.forge.modules.system.auth.dto.CaptchaResponse;
import com.forge.modules.system.auth.properties.CaptchaProperties;
import com.forge.modules.system.auth.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现
 * 使用 Hutool CircleCaptcha 生成验证码
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final StringRedisTemplate redisTemplate;
    private final CaptchaProperties properties;

    @Override
    public CaptchaResponse generate() {
        if (!properties.isEnabled()) {
            return new CaptchaResponse("", "");
        }

        // 生成验证码
        CircleCaptcha captcha = new CircleCaptcha(
                properties.getWidth(),
                properties.getHeight(),
                properties.getLength(),
                5  // 干扰圆数量
        );

        String captchaId = UUID.fastUUID().toString(true);
        String code = captcha.getCode();

        // 存储到 Redis
        String redisKey = properties.getRedisPrefix() + captchaId;
        redisTemplate.opsForValue().set(redisKey, code, properties.getExpireSeconds(), TimeUnit.SECONDS);

        // 返回 Base64 图片
        String imageBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(captcha.getImageBytes());

        log.debug("生成验证码: captchaId={}, code={}", captchaId, code);
        return new CaptchaResponse(captchaId, imageBase64);
    }

    @Override
    public boolean validate(String captchaId, String code) {
        if (!properties.isEnabled()) {
            return true;
        }

        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            return false;
        }

        String redisKey = properties.getRedisPrefix() + captchaId;
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            log.warn("验证码不存在或已过期: captchaId={}", captchaId);
            return false;
        }

        // 不区分大小写
        boolean valid = storedCode.equalsIgnoreCase(code);
        if (valid) {
            delete(captchaId);
        } else {
            log.warn("验证码校验失败: captchaId={}, expected={}, actual={}", captchaId, storedCode, code);
        }
        return valid;
    }

    @Override
    public void delete(String captchaId) {
        if (captchaId != null && !captchaId.isBlank()) {
            String redisKey = properties.getRedisPrefix() + captchaId;
            redisTemplate.delete(redisKey);
        }
    }
}
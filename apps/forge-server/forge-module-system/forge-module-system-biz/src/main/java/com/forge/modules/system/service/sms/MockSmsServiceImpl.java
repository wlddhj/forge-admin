package com.forge.modules.system.service.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Mock 短信服务实现
 * <p>
 * 开发环境使用，仅在控制台打印验证码，方便调试
 *
 * @author forge
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnMissingBean(SmsService.class)
public class MockSmsServiceImpl implements SmsService {

    @Override
    public void send(String phone, String code) {
        // Mock：控制台打印验证码，方便开发调试
        log.info("【Mock短信】手机号: {}, 验证码: {}", phone, code);
    }
}
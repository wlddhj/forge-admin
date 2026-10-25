package com.forge.modules.workflow.test;

import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.service.AiModelResolver;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * H2 端到端测试装配：真实引擎（flowlong starter 自动配置）+ workflow 模块真实组件，
 * 外部设施（WebSocket 推送 / LLM）用 Mockito stub 隔离。
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {QuartzAutoConfiguration.class})
@ComponentScan(basePackages = "com.forge.modules.workflow",
        excludeFilters = @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.REGEX,
                pattern = "com\\.forge\\.modules\\.workflow\\.controller\\..*"))
@MapperScan(basePackages = {
        "com.aizuda.bpm.mybatisplus.mapper",
        "com.forge.modules.workflow.mapper",
        "com.forge.modules.system.mapper"
})
public class WorkflowTestApplication {

    @TestConfiguration
    static class StubConfig {
        @Bean
        public NotificationService notificationService() {
            return org.mockito.Mockito.mock(NotificationService.class);
        }

        @Bean
        public LlmClient llmClient() {
            return org.mockito.Mockito.mock(LlmClient.class);
        }

        @Bean
        public AiModelResolver aiModelResolver() {
            return org.mockito.Mockito.mock(AiModelResolver.class);
        }

        @Bean
        public com.forge.modules.system.service.SysAttachmentService sysAttachmentService() {
            return org.mockito.Mockito.mock(com.forge.modules.system.service.SysAttachmentService.class);
        }

        @Bean
        public com.forge.modules.workflow.service.ProcessNoGenerator processNoGenerator() {
            return org.mockito.Mockito.mock(com.forge.modules.workflow.service.ProcessNoGenerator.class);
        }

        @Bean
        public com.forge.modules.system.api.tenant.TenantApi tenantApi() {
            return org.mockito.Mockito.mock(com.forge.modules.system.api.tenant.TenantApi.class);
        }

        @Bean
        public org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate() {
            // Redis 全家（CacheConfig/RedisConfig/DistributedLock）依赖此模板，mock 一次性满足
            return org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        }

        @Bean
        public com.forge.common.utils.CryptoUtils cryptoUtils() {
            // 测试用固定密钥（仅 H2 内存库加解密回环，不涉真实数据）
            return new com.forge.common.utils.CryptoUtils("test-aes-key-1234567890");
        }
    }
}

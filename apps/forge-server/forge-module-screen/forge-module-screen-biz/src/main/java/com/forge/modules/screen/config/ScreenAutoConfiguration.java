package com.forge.modules.screen.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 大屏模块自动装配入口。
 *
 * <p>通过 Spring Boot 的 {@code AutoConfiguration.imports} 机制注册：
 * <ul>
 *   <li>激活 {@link ScreenProperties}，绑定 {@code forge.security.screen.*} 配置；
 *   <li>组件扫描 {@code com.forge.modules.screen} 下所有
 *       {@code @RestController}/{@code @Service}/{@code @Component}，
 *       使大屏模块在被 {@code forge-server} 引入即可装配，无需额外 {@code @ComponentScan}。</li>
 * </ul>
 *
 * <p>本类使用 {@link AutoConfiguration} 注解（而非 {@code @Configuration}），
 * 以便 Spring Boot 在自动配置阶段优先处理；同时仍允许应用主类覆盖。
 *
 * @author standadmin
 */
@AutoConfiguration
@EnableConfigurationProperties(ScreenProperties.class)
@ComponentScan(basePackages = "com.forge.modules.screen")
public class ScreenAutoConfiguration {

    /**
     * 大屏 HTTP 数据源共用客户端：按 {@link ScreenProperties#getHttpTimeoutMs()}
     * 同时设置连接/读取超时——此前 {@code RestClient.create()} 无超时，
     * 导致 {@code http-timeout-ms} 配置对实际请求不生效。
     */
    @Bean
    public RestClient restClient(ScreenProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getHttpTimeoutMs());
        factory.setReadTimeout(props.getHttpTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }
}

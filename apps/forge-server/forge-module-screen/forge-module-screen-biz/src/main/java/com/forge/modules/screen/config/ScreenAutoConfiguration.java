package com.forge.modules.screen.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

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
}

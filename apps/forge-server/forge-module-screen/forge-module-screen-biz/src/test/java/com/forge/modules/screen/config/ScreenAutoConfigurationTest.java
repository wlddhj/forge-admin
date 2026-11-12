package com.forge.modules.screen.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScreenAutoConfiguration#restClient(ScreenProperties)} 超时收口单测：
 * RestClient.create() 无超时会使 http-timeout-ms 配置失效，
 * 此处直构断言 Bean 产出的 RestClient 内部 requestFactory 带 connect/read 双超时。
 */
class ScreenAutoConfigurationTest {

    @Test
    @DisplayName("restClient 按 httpTimeoutMs 设置连接/读取双超时")
    void restClient_usesTimeoutConfiguredFactory() throws Exception {
        ScreenProperties props = new ScreenProperties();
        props.setHttpTimeoutMs(3500);

        RestClient client = new ScreenAutoConfiguration().restClient(props);

        ClientHttpRequestFactory factory = extractRequestFactory(client);
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(intField(factory, "connectTimeout")).isEqualTo(3500);
        assertThat(intField(factory, "readTimeout")).isEqualTo(3500);
    }

    @Test
    @DisplayName("不同 httpTimeoutMs 构造出对应超时（防读默认值假阳性）")
    void restClient_reflectsChangedTimeout() throws Exception {
        ScreenProperties props = new ScreenProperties();
        props.setHttpTimeoutMs(8000);

        RestClient client = new ScreenAutoConfiguration().restClient(props);

        ClientHttpRequestFactory factory = extractRequestFactory(client);
        assertThat(intField(factory, "connectTimeout")).isEqualTo(8000);
        assertThat(intField(factory, "readTimeout")).isEqualTo(8000);
    }

    /**
     * 从 RestClient 实现内部取第一个非空的 ClientHttpRequestFactory 字段
     * （实现类私有字段名不耦合，按类型匹配遍历类层次）。
     */
    private static ClientHttpRequestFactory extractRequestFactory(RestClient client) throws IllegalAccessException {
        for (Class<?> c = client.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!ClientHttpRequestFactory.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                Object value = f.get(client);
                if (value instanceof ClientHttpRequestFactory factory) {
                    return factory;
                }
            }
        }
        throw new IllegalStateException("RestClient 内未找到 ClientHttpRequestFactory: " + client.getClass());
    }

    private static int intField(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(target);
    }
}

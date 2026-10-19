package com.forge.modules.system.service.storage;

import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.system.entity.SysFileConfig;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指纹 + 原子 compute 缓存语义验证（OSS/COS 与本类同构，不重复测试）
 */
class MinioStorageClientTest {

    private final MinioStorageClient client = new MinioStorageClient(new LocalCaches());

    private SysFileConfig config(String secretKey) {
        SysFileConfig config = new SysFileConfig();
        config.setId(1L);
        config.setEndpoint("http://localhost:9000");
        config.setAccessKey("ak");
        config.setSecretKey(secretKey);
        return config;
    }

    private MinioClient getClient(SysFileConfig config) throws Exception {
        Method method = MinioStorageClient.class.getDeclaredMethod("getClient", SysFileConfig.class);
        method.setAccessible(true);
        return (MinioClient) method.invoke(client, config);
    }

    @Test
    void sameFingerprintReusesClient() throws Exception {
        assertThat(getClient(config("sk-a"))).isSameAs(getClient(config("sk-a")));
    }

    @Test
    void fingerprintChangeRebuildsClient() throws Exception {
        MinioClient old = getClient(config("sk-a"));
        MinioClient rebuilt = getClient(config("sk-b"));
        assertThat(rebuilt).isNotSameAs(old);
        assertThat(getClient(config("sk-b"))).isSameAs(rebuilt);
    }

    @Test
    void evictRemovesClient() throws Exception {
        MinioClient first = getClient(config("sk-a"));
        client.evict(1L);
        assertThat(getClient(config("sk-a"))).isNotSameAs(first);
    }
}

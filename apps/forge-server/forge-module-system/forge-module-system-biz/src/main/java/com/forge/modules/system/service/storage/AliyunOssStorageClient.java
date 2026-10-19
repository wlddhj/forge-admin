package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.system.entity.SysFileConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.RemovalListener;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Objects;

/**
 * 阿里云 OSS 存储客户端（客户端实例按配置ID缓存，配置变更时 evict）。
 * 以「客户端构造字段指纹 + 原子 compute」保证配置变更后必定重建；
 * 客户端被替换/失效/驱逐时由 removalListener 统一 shutdown，防止连接资源泄漏
 */
@Component
public class AliyunOssStorageClient implements FileStorageClient {

    private final Cache<Long, CachedClient> clients;

    @SuppressWarnings("unchecked")
    public AliyunOssStorageClient(LocalCaches localCaches) {
        this.clients = localCaches.get("file-storage-oss",
                b -> b.maximumSize(64).expireAfterAccess(Duration.ofMinutes(30))
                        .removalListener((RemovalListener<Object, Object>) notification -> {
                            if (notification.getValue() instanceof CachedClient removed) {
                                removed.client().shutdown();
                            }
                        }));
    }

    @Override
    public String storageType() {
        return "aliyun_oss";
    }

    @Override
    public StorageResult upload(SysFileConfig config, MultipartFile file, String key) throws Exception {
        getClient(config).putObject(config.getBucketName(), key, new ByteArrayInputStream(file.getBytes()));
        String domain = config.getDomain();
        String fileUrl = StrUtil.isNotBlank(domain)
                ? domain + "/" + key
                : "https://" + config.getBucketName() + "." + config.getEndpoint() + "/" + key;
        return new StorageResult(config.getBucketName() + "/" + key, fileUrl);
    }

    @Override
    public void delete(SysFileConfig config, String filePath) {
        getClient(config).deleteObject(config.getBucketName(), extractKey(filePath));
    }

    @Override
    public void evict(Long configId) {
        clients.invalidate(configId);
    }

    private OSS getClient(SysFileConfig config) {
        String fingerprint = String.join("|",
                Objects.toString(config.getEndpoint(), ""),
                Objects.toString(config.getAccessKey(), ""),
                Objects.toString(config.getSecretKey(), ""));
        CachedClient cached = clients.asMap().compute(config.getId(), (id, existing) ->
                existing != null && existing.fingerprint().equals(fingerprint)
                        ? existing
                        : new CachedClient(fingerprint, build(config)));
        return cached.client();
    }

    private OSS build(SysFileConfig config) {
        return new OSSClientBuilder()
                .build(config.getEndpoint(), config.getAccessKey(), config.getSecretKey());
    }

    private String extractKey(String filePath) {
        return filePath.contains("/") ? filePath.substring(filePath.indexOf("/") + 1) : filePath;
    }

    private record CachedClient(String fingerprint, OSS client) {
    }
}

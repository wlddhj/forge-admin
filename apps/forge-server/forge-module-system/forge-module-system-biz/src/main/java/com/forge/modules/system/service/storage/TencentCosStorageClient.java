package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.system.entity.SysFileConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.RemovalListener;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Objects;

/**
 * 腾讯云 COS 存储客户端（客户端实例按配置ID缓存，配置变更时 evict）。
 * 以「客户端构造字段指纹 + 原子 compute」保证配置变更后必定重建；
 * 客户端被替换/失效/驱逐时由 removalListener 统一 shutdown，防止连接资源泄漏
 */
@Component
public class TencentCosStorageClient implements FileStorageClient {

    private final Cache<Long, CachedClient> clients;

    @SuppressWarnings("unchecked")
    public TencentCosStorageClient(LocalCaches localCaches) {
        this.clients = localCaches.get("file-storage-cos",
                b -> b.maximumSize(64).expireAfterAccess(Duration.ofMinutes(30))
                        .removalListener((RemovalListener<Object, Object>) notification -> {
                            if (notification.getValue() instanceof CachedClient removed) {
                                removed.client().shutdown();
                            }
                        }));
    }

    @Override
    public String storageType() {
        return "tencent_cos";
    }

    @Override
    public StorageResult upload(SysFileConfig config, MultipartFile file, String key) throws Exception {
        com.qcloud.cos.model.ObjectMetadata metadata = new com.qcloud.cos.model.ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (StrUtil.isNotBlank(file.getContentType())) {
            metadata.setContentType(file.getContentType());
        }
        getClient(config).putObject(config.getBucketName(), key,
                new ByteArrayInputStream(file.getBytes()), metadata);
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

    private COSClient getClient(SysFileConfig config) {
        String fingerprint = String.join("|",
                Objects.toString(config.getAccessKey(), ""),
                Objects.toString(config.getSecretKey(), ""),
                Objects.toString(config.getEndpoint(), ""));
        CachedClient cached = clients.asMap().compute(config.getId(), (id, existing) ->
                existing != null && existing.fingerprint().equals(fingerprint)
                        ? existing
                        : new CachedClient(fingerprint, build(config)));
        return cached.client();
    }

    private COSClient build(SysFileConfig config) {
        BasicCOSCredentials credentials =
                new BasicCOSCredentials(config.getAccessKey(), config.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(extractRegionFromEndpoint(config.getEndpoint())));
        return new COSClient(credentials, clientConfig);
    }

    private String extractRegionFromEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint)) {
            return "ap-guangzhou";
        }
        String[] parts = endpoint.replace("https://", "").replace("http://", "").split("\\.");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "ap-guangzhou";
    }

    private String extractKey(String filePath) {
        return filePath.contains("/") ? filePath.substring(filePath.indexOf("/") + 1) : filePath;
    }

    private record CachedClient(String fingerprint, COSClient client) {
    }
}

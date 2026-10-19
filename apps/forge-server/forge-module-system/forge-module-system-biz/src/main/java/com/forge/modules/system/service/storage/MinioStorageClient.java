package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.system.entity.SysFileConfig;
import com.google.common.cache.Cache;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Objects;

/**
 * MinIO 存储客户端（客户端实例按配置ID缓存，配置变更时 evict）。
 * 以「客户端构造字段指纹 + 原子 compute」保证配置变更后必定重建，
 * maximumSize/expireAfterAccess 兜底防止配置删除后实例永久驻留
 */
@Component
public class MinioStorageClient implements FileStorageClient {

    private final Cache<Long, CachedClient> clients;

    public MinioStorageClient(LocalCaches localCaches) {
        this.clients = localCaches.get("file-storage-minio",
                b -> b.maximumSize(64).expireAfterAccess(Duration.ofMinutes(30)));
    }

    @Override
    public String storageType() {
        return "minio";
    }

    @Override
    public StorageResult upload(SysFileConfig config, MultipartFile file, String key) throws Exception {
        MinioClient client = getClient(config);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(file.getBytes())) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(config.getBucketName())
                    .object(key)
                    .stream(bis, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
        String domain = config.getDomain();
        String fileUrl = StrUtil.isNotBlank(domain)
                ? domain + "/" + key
                : config.getEndpoint() + "/" + config.getBucketName() + "/" + key;
        return new StorageResult(config.getBucketName() + "/" + key, fileUrl);
    }

    @Override
    public void delete(SysFileConfig config, String filePath) throws Exception {
        getClient(config).removeObject(RemoveObjectArgs.builder()
                .bucket(config.getBucketName())
                .object(extractKey(filePath))
                .build());
    }

    @Override
    public void evict(Long configId) {
        clients.invalidate(configId);
    }

    private MinioClient getClient(SysFileConfig config) {
        String fingerprint = String.join("|",
                Objects.toString(config.getEndpoint(), ""),
                Objects.toString(config.getAccessKey(), ""),
                Objects.toString(config.getSecretKey(), ""));
        CachedClient cached = clients.asMap().compute(config.getId(), (id, existing) ->
                existing != null && existing.fingerprint.equals(fingerprint)
                        ? existing
                        : new CachedClient(fingerprint, build(config)));
        return cached.client;
    }

    private MinioClient build(SysFileConfig config) {
        return MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
    }

    private String extractKey(String filePath) {
        return filePath.contains("/") ? filePath.substring(filePath.indexOf("/") + 1) : filePath;
    }

    private record CachedClient(String fingerprint, MinioClient client) {
    }
}

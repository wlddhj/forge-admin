package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.forge.modules.system.entity.SysFileConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MinIO 存储客户端（客户端实例按配置ID缓存，配置变更时 evict）
 */
@Component
public class MinioStorageClient implements FileStorageClient {

    private final Map<Long, MinioClient> clients = new ConcurrentHashMap<>();

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
        clients.remove(configId);
    }

    private MinioClient getClient(SysFileConfig config) {
        return clients.computeIfAbsent(config.getId(), id -> MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build());
    }

    private String extractKey(String filePath) {
        return filePath.contains("/") ? filePath.substring(filePath.indexOf("/") + 1) : filePath;
    }
}

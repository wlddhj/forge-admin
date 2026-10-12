package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.forge.modules.system.entity.SysFileConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 腾讯云 COS 存储客户端（客户端实例按配置ID缓存，配置变更时 evict）
 */
@Component
public class TencentCosStorageClient implements FileStorageClient {

    private final Map<Long, COSClient> clients = new ConcurrentHashMap<>();

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
        COSClient client = clients.remove(configId);
        if (client != null) {
            client.shutdown();
        }
    }

    private COSClient getClient(SysFileConfig config) {
        return clients.computeIfAbsent(config.getId(), id -> {
            BasicCOSCredentials credentials =
                    new BasicCOSCredentials(config.getAccessKey(), config.getSecretKey());
            ClientConfig clientConfig = new ClientConfig(new Region(extractRegionFromEndpoint(config.getEndpoint())));
            return new COSClient(credentials, clientConfig);
        });
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
}

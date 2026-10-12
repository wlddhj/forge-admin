package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.forge.modules.system.entity.SysFileConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿里云 OSS 存储客户端（客户端实例按配置ID缓存，配置变更时 evict）
 */
@Component
public class AliyunOssStorageClient implements FileStorageClient {

    private final Map<Long, OSS> clients = new ConcurrentHashMap<>();

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
        OSS client = clients.remove(configId);
        if (client != null) {
            client.shutdown();
        }
    }

    private OSS getClient(SysFileConfig config) {
        return clients.computeIfAbsent(config.getId(), id -> new OSSClientBuilder()
                .build(config.getEndpoint(), config.getAccessKey(), config.getSecretKey()));
    }

    private String extractKey(String filePath) {
        return filePath.contains("/") ? filePath.substring(filePath.indexOf("/") + 1) : filePath;
    }
}

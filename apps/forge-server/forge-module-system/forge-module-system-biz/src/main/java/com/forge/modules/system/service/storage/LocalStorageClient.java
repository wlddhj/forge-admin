package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.forge.modules.system.entity.SysFileConfig;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储客户端
 */
@Component
public class LocalStorageClient implements FileStorageClient {

    private final ServerProperties serverProperties;

    public LocalStorageClient(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @Override
    public String storageType() {
        return "local";
    }

    @Override
    public StorageResult upload(SysFileConfig config, InputStream in, long size, String contentType, String key) throws IOException {
        String basePath = StrUtil.blankToDefault(config.getBasePath(), "./uploads");
        Path absoluteBasePath = Paths.get(basePath).toAbsolutePath().normalize();

        Path dirPath = absoluteBasePath.resolve(Paths.get(key).getParent());
        Files.createDirectories(dirPath);

        String newFileName = key.substring(key.lastIndexOf("/") + 1);
        Path filePath = dirPath.resolve(newFileName);
        try (in) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        String domain = StrUtil.blankToDefault(config.getDomain(), buildDefaultDomain(absoluteBasePath));
        String fileUrl = domain + "/" + key;

        return new StorageResult(filePath.toString(), fileUrl);
    }

    @Override
    public InputStream download(SysFileConfig config, String filePath) throws IOException {
        return Files.newInputStream(Paths.get(filePath));
    }

    @Override
    public void delete(SysFileConfig config, String filePath) throws IOException {
        Files.deleteIfExists(Paths.get(filePath));
    }

    private String buildDefaultDomain(Path absoluteBasePath) {
        int port = serverProperties.getPort() != null ? serverProperties.getPort() : 8080;
        String contextPath = StrUtil.nullToEmpty(serverProperties.getServlet().getContextPath());
        return "http://localhost:" + port + contextPath + "/" + absoluteBasePath.getFileName();
    }
}

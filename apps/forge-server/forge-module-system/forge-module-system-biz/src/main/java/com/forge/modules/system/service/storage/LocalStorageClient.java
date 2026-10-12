package com.forge.modules.system.service.storage;

import cn.hutool.core.util.StrUtil;
import com.forge.modules.system.entity.SysFileConfig;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public StorageResult upload(SysFileConfig config, MultipartFile file, String key) throws IOException {
        String basePath = StrUtil.blankToDefault(config.getBasePath(), "./uploads");
        Path absoluteBasePath = Paths.get(basePath).toAbsolutePath().normalize();

        Path dirPath = absoluteBasePath.resolve(Paths.get(key).getParent());
        Files.createDirectories(dirPath);

        String newFileName = key.substring(key.lastIndexOf("/") + 1);
        Path filePath = dirPath.resolve(newFileName);
        file.transferTo(filePath.toFile());

        String domain = StrUtil.blankToDefault(config.getDomain(), buildDefaultDomain(absoluteBasePath));
        String fileUrl = domain + "/" + key;

        return new StorageResult(filePath.toString(), fileUrl);
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

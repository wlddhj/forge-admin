package com.forge.modules.system.service.storage;

import com.forge.modules.system.entity.SysFileConfig;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储客户端策略接口
 */
public interface FileStorageClient {

    /**
     * 支持的存储类型，对应 sys_file_config.storage_type
     */
    String storageType();

    /**
     * 上传文件
     *
     * @param config 存储配置
     * @param file   上传文件
     * @param key    存储对象键（日期目录/UUID文件名）
     * @return 落库用的文件路径与访问 URL
     */
    StorageResult upload(SysFileConfig config, MultipartFile file, String key) throws Exception;

    /**
     * 删除文件
     *
     * @param config   存储配置（本地存储不使用）
     * @param filePath sys_attachment.file_path 落库值（本地为绝对路径，云端为 bucket/key）
     */
    void delete(SysFileConfig config, String filePath) throws Exception;

    /**
     * 配置变更时失效缓存的客户端实例
     */
    default void evict(Long configId) {
    }
}

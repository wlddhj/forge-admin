package com.forge.modules.system.service.storage;

import com.forge.modules.system.entity.SysFileConfig;

import java.io.InputStream;

/**
 * 文件存储客户端策略接口（面向流，不依赖 web 层类型）
 */
public interface FileStorageClient {

    /**
     * 支持的存储类型，对应 sys_file_config.storage_type
     */
    String storageType();

    /**
     * 上传文件
     *
     * @param config      存储配置
     * @param in          文件内容流（由调用方负责关闭）
     * @param size        文件字节数
     * @param contentType MIME 类型（可空，local 存储不使用）
     * @param key         存储对象键（日期目录/UUID文件名）
     * @return 落库用的文件路径与访问 URL
     */
    StorageResult upload(SysFileConfig config, InputStream in, long size, String contentType, String key) throws Exception;

    /**
     * 读取文件内容
     *
     * @param config   存储配置（本地存储不使用）
     * @param filePath sys_attachment.file_path 落库值（本地为绝对路径，云端为 bucket/key）
     * @return 内容流，由调用方负责关闭
     */
    InputStream download(SysFileConfig config, String filePath) throws Exception;

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

package com.forge.modules.system.service.storage;

/**
 * 存储上传结果：filePath 为 sys_attachment.file_path 落库值
 */
public record StorageResult(String filePath, String fileUrl) {
}

package com.forge.modules.ai.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析器：提取文本内容（替代原 Python pypdf/python-docx/txt 解析）
 */
public interface DocumentParser {

    /**
     * 解析文件字节为纯文本
     *
     * @param bytes    文件字节
     * @param fileName 原始文件名（用于扩展名白名单校验）
     * @return 提取的文本内容
     * @throws IllegalArgumentException 格式不支持或文件超限
     */
    String parse(byte[] bytes, String fileName);

    /**
     * 解析上传文件为纯文本
     */
    default String parse(MultipartFile file) {
        try {
            return parse(file.getBytes(), file.getOriginalFilename());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 支持的扩展名白名单
     */
    boolean isSupported(String fileName);
}

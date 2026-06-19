package com.forge.modules.system.auth.util;

import com.forge.modules.system.auth.properties.UploadPolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传安全校验器
 *
 * @author standadmin
 */
@Component
@RequiredArgsConstructor
public class FileUploadValidator {

    private final UploadPolicyProperties properties;

    /**
     * Magic Number 映射（文件真实类型识别）
     */
    private static final Map<String, byte[]> MAGIC_NUMBERS = new HashMap<>();
    static {
        // 图片
        MAGIC_NUMBERS.put("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC_NUMBERS.put("jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC_NUMBERS.put("png", new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47});
        MAGIC_NUMBERS.put("gif", new byte[]{(byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38});
        MAGIC_NUMBERS.put("webp", new byte[]{(byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46});
        MAGIC_NUMBERS.put("bmp", new byte[]{(byte) 0x42, (byte) 0x4D});
        // 文档
        MAGIC_NUMBERS.put("pdf", new byte[]{(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46}); // %PDF
    }

    /**
     * 校验文件
     *
     * @param file 上传的文件
     * @return 校验通过返回 null，否则返回错误信息
     */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "上传文件不能为空";
        }

        // 1. 文件大小校验
        if (file.getSize() > properties.getMaxSize()) {
            return "文件大小超过限制（最大 " + (properties.getMaxSize() / 1024 / 1024) + "MB）";
        }

        // 2. 扩展名白名单校验
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return "无法获取文件名";
        }
        String extension = getFileExtension(originalName);
        List<String> allowed = properties.getAllowedExtensions();
        if (!allowed.contains(extension.toLowerCase())) {
            return "不允许的文件类型：" + extension;
        }

        // 3. Magic Number 校验（可选）
        if (properties.isEnableMagicCheck()) {
            String magicError = validateMagicNumber(file, extension);
            if (magicError != null) {
                return magicError;
            }
        }

        return null; // 校验通过
    }

    /**
     * Magic Number 校验
     */
    private String validateMagicNumber(MultipartFile file, String declaredExtension) {
        try {
            byte[] content = file.getBytes();
            if (content.length < 4) {
                return "文件内容太小，无法校验类型";
            }

            byte[] expectedMagic = MAGIC_NUMBERS.get(declaredExtension.toLowerCase());
            if (expectedMagic == null) {
                // 该扩展名没有 Magic Number 定义，跳过校验
                return null;
            }

            for (int i = 0; i < expectedMagic.length; i++) {
                if (content[i] != expectedMagic[i]) {
                    return "文件真实类型与声明类型不符，可能存在伪装风险";
                }
            }

            return null;
        } catch (IOException e) {
            return "无法读取文件内容进行校验";
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}
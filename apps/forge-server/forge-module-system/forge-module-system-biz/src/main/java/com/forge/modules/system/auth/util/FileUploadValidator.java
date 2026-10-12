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
     * Magic Number 映射（文件真实类型识别），同一扩展名需匹配全部签名
     */
    private static final Map<String, List<MagicSignature>> MAGIC_NUMBERS = new HashMap<>();

    /**
     * 文件头签名：magic 在文件起始偏移 offset 处出现
     */
    private record MagicSignature(byte[] magic, int offset) {
    }

    static {
        // 图片
        MAGIC_NUMBERS.put("jpg", List.of(new MagicSignature(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0)));
        MAGIC_NUMBERS.put("jpeg", List.of(new MagicSignature(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0)));
        MAGIC_NUMBERS.put("png", List.of(new MagicSignature(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, 0)));
        MAGIC_NUMBERS.put("gif", List.of(new MagicSignature(new byte[]{0x47, 0x49, 0x46, 0x38}, 0)));
        // RIFF 容器头与 wav/avi 共用，需叠加 offset 8 处的 WEBP 标识
        MAGIC_NUMBERS.put("webp", List.of(
                new MagicSignature(new byte[]{0x52, 0x49, 0x46, 0x46}, 0),
                new MagicSignature(new byte[]{0x57, 0x45, 0x42, 0x50}, 8)));
        MAGIC_NUMBERS.put("bmp", List.of(new MagicSignature(new byte[]{0x42, 0x4D}, 0)));
        // 文档
        MAGIC_NUMBERS.put("pdf", List.of(new MagicSignature(new byte[]{0x25, 0x50, 0x44, 0x46}, 0))); // %PDF
        // OLE2 复合文档（doc/xls/ppt 共用，无法仅凭文件头互斥）
        byte[] ole2 = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        MAGIC_NUMBERS.put("doc", List.of(new MagicSignature(ole2, 0)));
        MAGIC_NUMBERS.put("xls", List.of(new MagicSignature(ole2, 0)));
        MAGIC_NUMBERS.put("ppt", List.of(new MagicSignature(ole2, 0)));
        // OOXML 为 zip 容器（docx/xlsx/pptx 共用 PK 头）
        byte[] zip = new byte[]{0x50, 0x4B, 0x03, 0x04};
        MAGIC_NUMBERS.put("docx", List.of(new MagicSignature(zip, 0)));
        MAGIC_NUMBERS.put("xlsx", List.of(new MagicSignature(zip, 0)));
        MAGIC_NUMBERS.put("pptx", List.of(new MagicSignature(zip, 0)));
        // txt/csv/md/json 无可靠文件头，不定义即跳过校验
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

            List<MagicSignature> signatures = MAGIC_NUMBERS.get(declaredExtension.toLowerCase());
            if (signatures == null) {
                // 该扩展名没有 Magic Number 定义，跳过校验
                return null;
            }

            for (MagicSignature signature : signatures) {
                int end = signature.offset() + signature.magic().length;
                if (content.length < end) {
                    return "文件真实类型与声明类型不符，可能存在伪装风险";
                }
                for (int i = 0; i < signature.magic().length; i++) {
                    if (content[signature.offset() + i] != signature.magic()[i]) {
                        return "文件真实类型与声明类型不符，可能存在伪装风险";
                    }
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
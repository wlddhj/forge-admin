package com.forge.modules.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于 Apache Tika 的文档解析实现：pdf/docx/txt
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt");
    private static final long MAX_SIZE = 10 * 1024 * 1024L;

    @Override
    public boolean isSupported(String fileName) {
        return SUPPORTED_EXTENSIONS.contains(extension(fileName));
    }

    @Override
    public String parse(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        if (bytes.length > MAX_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }
        if (!isSupported(fileName)) {
            throw new IllegalArgumentException("仅支持 pdf/docx/txt 格式的文档");
        }

        try {
            TikaDocumentReader reader = new TikaDocumentReader(new ByteArrayResource(bytes));
            List<Document> documents = reader.read();
            StringBuilder content = new StringBuilder();
            for (Document document : documents) {
                String text = document.getFormattedContent();
                if (text != null && !text.isBlank()) {
                    if (content.length() > 0) {
                        content.append("\n\n");
                    }
                    content.append(text.trim());
                }
            }
            String result = content.toString();
            if (result.isEmpty()) {
                throw new IllegalArgumentException("未能从文档中提取到文本内容");
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档解析失败: {}", e.getMessage());
            throw new IllegalArgumentException("文档解析失败: " + e.getMessage());
        }
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

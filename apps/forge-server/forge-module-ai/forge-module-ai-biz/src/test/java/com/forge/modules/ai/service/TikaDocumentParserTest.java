package com.forge.modules.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaDocumentParserTest {

    private TikaDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new TikaDocumentParser();
    }

    @Test
    void 解析txt内容() {
        String content = parser.parse("你好，文档内容".getBytes(StandardCharsets.UTF_8), "a.txt");
        assertThat(content).contains("你好").contains("文档内容");
    }

    @Test
    void 白名单_pdf_docx_txt() {
        assertThat(parser.isSupported("a.pdf")).isTrue();
        assertThat(parser.isSupported("b.DOCX")).isTrue();
        assertThat(parser.isSupported("c.txt")).isTrue();
        assertThat(parser.isSupported("d.doc")).isFalse();
        assertThat(parser.isSupported("e.xlsx")).isFalse();
        assertThat(parser.isSupported(null)).isFalse();
    }

    @Test
    void 不支持的扩展名报错() {
        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> parser.parse(bytes, "a.docx.exe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pdf/docx/txt");
    }

    @Test
    void 超过10MB报错() {
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> parser.parse(bytes, "a.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void 空内容报错() {
        assertThatThrownBy(() -> parser.parse(new byte[0], "a.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");
    }
}

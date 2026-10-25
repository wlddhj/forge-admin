package com.forge.modules.ai.agent.tools;

import com.forge.modules.ai.agent.AgentProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 工具字节上限读取单测：超限截断/恰好等于上限不误标/空流/UTF-8 中文（AC1）
 */
class HttpFetchToolTest {

    @Test
    void 超上限截断并标注() throws IOException {
        byte[] data = "x".repeat(3000).getBytes(StandardCharsets.UTF_8);
        HttpFetchTool.CappedRead read = HttpFetchTool.readCapped(new ByteArrayInputStream(data), 1024);
        assertTrue(read.truncated());
        assertEquals(1024, read.body().length());
    }

    @Test
    void 恰好等于上限不误标截断() throws IOException {
        byte[] data = "y".repeat(1024).getBytes(StandardCharsets.UTF_8);
        HttpFetchTool.CappedRead read = HttpFetchTool.readCapped(new ByteArrayInputStream(data), 1024);
        assertFalse(read.truncated());
        assertEquals(1024, read.body().length());
    }

    @Test
    void 低于上限全量读取() throws IOException {
        HttpFetchTool.CappedRead read = HttpFetchTool.readCapped(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 1024);
        assertFalse(read.truncated());
        assertEquals("hello", read.body());
    }

    @Test
    void 空流返回空串() throws IOException {
        HttpFetchTool.CappedRead read = HttpFetchTool.readCapped(
                new ByteArrayInputStream(new byte[0]), 1024);
        assertFalse(read.truncated());
        assertEquals("", read.body());
    }

    @Test
    void 中文UTF8按字节上限截断() throws IOException {
        // 中文 3 字节/字:上限 7 字节 = 2 字 + 1 字节残缺(Decoder 替换)
        InputStream in = new ByteArrayInputStream("你好世界".getBytes(StandardCharsets.UTF_8));
        HttpFetchTool.CappedRead read = HttpFetchTool.readCapped(in, 7);
        assertTrue(read.truncated());
        assertTrue(read.body().startsWith("你"));
    }

    @Test
    void 通配符放行全部域名() {
        HttpFetchTool tool = new HttpFetchTool(new AgentProperties());
        assertTrue(tool.hostAllowed("anything.example.com", List.of("*")));
        assertTrue(tool.hostAllowed("127.0.0.1", List.of("*")));
    }

    @Test
    void 后缀匹配含子域名与裸域名() {
        HttpFetchTool tool = new HttpFetchTool(new AgentProperties());
        assertTrue(tool.hostAllowed("api.github.com", List.of("github.com")));
        assertTrue(tool.hostAllowed("github.com", List.of("github.com")));
        assertTrue(tool.hostAllowed("deep.api.github.com", List.of(".github.com")));
        assertFalse(tool.hostAllowed("github.evil.com", List.of("github.com")), "子串不等于后缀匹配");
        assertFalse(tool.hostAllowed("example.com", List.of("github.com")));
    }
}

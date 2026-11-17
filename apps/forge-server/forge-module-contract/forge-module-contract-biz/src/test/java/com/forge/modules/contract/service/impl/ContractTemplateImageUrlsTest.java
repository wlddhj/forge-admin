package com.forge.modules.contract.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模板内容图片 URL 提取（Jsoup）：data: 内联图跳过，多图去重
 */
class ContractTemplateImageUrlsTest {

    private final ContractTemplateServiceImpl service = new ContractTemplateServiceImpl(
            null, null, null, null, null, null, null);

    @SuppressWarnings("unchecked")
    private Set<String> extract(String html) throws Exception {
        Method method = ContractTemplateServiceImpl.class.getDeclaredMethod("extractImageUrls", String.class);
        method.setAccessible(true);
        return (Set<String>) method.invoke(service, html);
    }

    @Test
    void extractsImgSrcs() throws Exception {
        Set<String> urls = extract("<p><img src=\"http://localhost:8180/uploads/2026/11/16/a.png\">"
                + "<img src=\"https://cdn.example.com/x.png\"></p>");
        assertThat(urls).containsExactlyInAnyOrder(
                "http://localhost:8180/uploads/2026/11/16/a.png", "https://cdn.example.com/x.png");
    }

    @Test
    void skipsDataUrisAndDuplicates() throws Exception {
        Set<String> urls = extract("<img src=\"data:image/png;base64,xxxx\">"
                + "<img src=\"/uploads/a.png\"><img src=\"/uploads/a.png\">");
        assertThat(urls).containsExactly("/uploads/a.png");
    }

    @Test
    void blankContentYieldsEmpty() throws Exception {
        assertThat(extract(null)).isEmpty();
        assertThat(extract("   ")).isEmpty();
        assertThat(extract("<p>无图内容</p>")).isEmpty();
    }
}

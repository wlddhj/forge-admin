package com.forge.modules.ai.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.entity.AiDocument;
import com.forge.modules.ai.enums.DocumentStatus;
import com.forge.modules.ai.mapper.AiDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档知识库检索工具：按关键词检索已解析完成的文档（文件名/摘要/正文 OR LIKE），
 * 正文命中返回上下文窗口片段（不回传整篇）；只读、非高危。
 * 数据可见性：部署内共享知识库语义（不过滤上传者）；多租户部署注意 ai_document 无 tenant_id 列
 * （租户拦截器开启时该表查询将报错而非泄露，存量边界，见表外遗留 ai-agent-opt-round2）。
 */
@Component
@RequiredArgsConstructor
public class DocumentSearchTool implements AgentTool {

    /** content 命中片段的窗口：关键词前 100 / 后 150 字符 */
    static final int SNIPPET_BEFORE = 100;
    static final int SNIPPET_AFTER = 150;
    /** 摘要回传上限 */
    static final int SUMMARY_LIMIT = 200;

    private final AiDocumentMapper documentMapper;
    private final AgentProperties properties;

    @Override
    public String name() {
        return "document_search";
    }

    @Override
    public String description() {
        return "检索知识库中已上传解析的文档（按关键词匹配文件名/摘要/正文，返回最相关的文档片段），回答文档相关问题应优先使用";
    }

    @Override
    public Map<String, String> parameterSpec() {
        return Map.of(
                "keyword", "检索关键词（非空）",
                "limit", "可选，返回条数上限，默认 5");
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String keyword = normalizeKeyword(String.valueOf(args.get("keyword")));
        int limit = resolveLimit(args.get("limit"));
        String escaped = escapeLike(keyword);

        List<AiDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getStatus, DocumentStatus.COMPLETED.getCode())
                .and(w -> w.like(AiDocument::getFileName, escaped)
                        .or().like(AiDocument::getSummary, escaped)
                        .or().like(AiDocument::getContent, escaped))
                .orderByDesc(AiDocument::getUpdateTime)
                .last("LIMIT " + (limit + 1)));

        boolean overflow = docs.size() > limit;
        List<Map<String, Object>> results = new ArrayList<>();
        for (AiDocument doc : docs.subList(0, Math.min(docs.size(), limit))) {
            results.add(toResult(doc, keyword));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        response.put("total", results.size());
        if (overflow) {
            response.put("overflowOmitted", true);
        }
        return response;
    }

    private Map<String, Object> toResult(AiDocument doc, String keyword) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", doc.getId());
        result.put("fileName", doc.getFileName());
        String summary = doc.getSummary() == null ? "" : doc.getSummary();
        result.put("summary", truncate(summary, SUMMARY_LIMIT));
        String matchedField = firstMatchedField(doc, keyword);
        result.put("matchedField", matchedField);
        if ("content".equals(matchedField)) {
            result.put("snippet", snippet(doc.getContent(), keyword));
        }
        return result;
    }

    /** 命中字段优先级：fileName > summary > content（决定是否附正文片段） */
    private String firstMatchedField(AiDocument doc, String keyword) {
        if (containsIgnoreCase(doc.getFileName(), keyword)) {
            return "fileName";
        }
        if (containsIgnoreCase(doc.getSummary(), keyword)) {
            return "summary";
        }
        return "content";
    }

    /** 关键词上下文窗口片段；未命中或正文过短返回整段截断 */
    private String snippet(String content, String keyword) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int idx = indexOfIgnoreCase(content, keyword);
        if (idx < 0) {
            return truncate(content, SNIPPET_BEFORE + SNIPPET_AFTER);
        }
        int start = Math.max(0, idx - SNIPPET_BEFORE);
        int end = Math.min(content.length(), idx + keyword.length() + SNIPPET_AFTER);
        return (start > 0 ? "…" : "") + content.substring(start, end) + (end < content.length() ? "…" : "");
    }

    private String normalizeKeyword(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            throw new BusinessException("keyword 不能为空");
        }
        String keyword = raw.trim();
        if (keyword.length() > 100) {
            keyword = keyword.substring(0, 100);
        }
        return keyword;
    }

    private int resolveLimit(Object raw) {
        if (raw instanceof Number n) {
            return Math.max(1, Math.min(10, n.intValue()));
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Math.max(1, Math.min(10, Integer.parseInt(s.trim())));
            } catch (NumberFormatException ignored) {
                // 非数字按默认值
            }
        }
        return 5;
    }

    /** LIKE 通配符转义（配合 ESCAPE '\\'，防语义漂移与全表 '%' 扫描） */
    static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean containsIgnoreCase(String text, String keyword) {
        return text != null && indexOfIgnoreCase(text, keyword) >= 0;
    }

    private static int indexOfIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) {
            return -1;
        }
        return text.toLowerCase().indexOf(keyword.toLowerCase());
    }

    private String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "…" : text;
    }
}

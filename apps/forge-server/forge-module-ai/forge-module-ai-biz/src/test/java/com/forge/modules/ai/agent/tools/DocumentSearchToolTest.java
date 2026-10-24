package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.entity.AiDocument;
import com.forge.modules.ai.mapper.AiDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 文档检索工具单测：命中/未命中/片段窗口/通配符转义/仅文件名命中/limit 边界（AC1）
 */
@ExtendWith(MockitoExtension.class)
class DocumentSearchToolTest {

    @Mock
    private AiDocumentMapper documentMapper;

    private DocumentSearchTool tool;

    @BeforeEach
    void setUp() {
        tool = new DocumentSearchTool(documentMapper, new AgentProperties());
    }

    @Test
    void 正文命中返回上下文片段而非整篇() {
        String head = "开头无关内容。".repeat(50);
        String hit = "本季度销售目标为1000万元，达成率85%";
        String tail = "结尾无关内容。".repeat(50);
        when(documentMapper.selectList(any())).thenReturn(List.of(
                doc(1L, "销售报告.pdf", "销售摘要", head + hit + tail)));

        Map<?, ?> result = (Map<?, ?>) tool.execute(Map.of("keyword", "销售目标"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertEquals(1, results.size());
        assertEquals("content", results.get(0).get("matchedField"));
        String snippet = String.valueOf(results.get(0).get("snippet"));
        assertTrue(snippet.contains("销售目标"));
        // 片段窗口远小于整篇正文
        assertTrue(snippet.length() < 400);
        assertTrue(snippet.startsWith("…"));
    }

    @Test
    void 文件名命中优先且不附正文片段() {
        when(documentMapper.selectList(any())).thenReturn(List.of(
                doc(2L, "库存管理制度.docx", null, "很长的正文……")));

        Map<?, ?> result = (Map<?, ?>) tool.execute(Map.of("keyword", "库存管理"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertEquals("fileName", results.get(0).get("matchedField"));
        assertFalse(results.get(0).containsKey("snippet"));
    }

    @Test
    void 未命中返回空结果集() {
        when(documentMapper.selectList(any())).thenReturn(List.of());
        Map<?, ?> result = (Map<?, ?>) tool.execute(Map.of("keyword", "不存在"));
        assertEquals(0, result.get("total"));
    }

    @Test
    void 超出limit标注溢出() {
        when(documentMapper.selectList(any())).thenReturn(List.of(
                doc(1L, "a.md", null, "目标内容"),
                doc(2L, "b.md", null, "目标内容"),
                doc(3L, "c.md", null, "目标内容")));

        Map<?, ?> result = (Map<?, ?>) tool.execute(Map.of("keyword", "目标", "limit", 2));
        assertEquals(2, result.get("total"));
        assertEquals(Boolean.TRUE, result.get("overflowOmitted"));
    }

    @Test
    void 空关键词与null参数拒绝() {
        assertThrows(BusinessException.class, () -> tool.execute(Map.of("keyword", " ")));
        assertThrows(BusinessException.class, () -> tool.execute(Map.of()));
    }

    @Test
    void LIKE通配符转义() {
        assertEquals("100\\%完成", DocumentSearchTool.escapeLike("100%完成"));
        assertEquals("a\\_b", DocumentSearchTool.escapeLike("a_b"));
        assertEquals("a\\\\b", DocumentSearchTool.escapeLike("a\\b"));
    }

    @Test
    void limit参数边界收敛() {
        when(documentMapper.selectList(any())).thenReturn(List.of());
        tool.execute(Map.of("keyword", "x", "limit", 99));   // 收敛到 10
        tool.execute(Map.of("keyword", "x", "limit", "3"));  // 字符串数字
        tool.execute(Map.of("keyword", "x", "limit", "abc")); // 非法按默认 5
    }

    @Test
    void 工具属性() {
        assertEquals("document_search", tool.name());
        assertFalse(tool.highRisk());
        assertTrue(tool.description().contains("知识库"));
    }

    @Test
    void 会话用户仅检索本人文档_无用户全量() {
        // 注册 TableInfo 使 Lambda 条件可渲染(复刻 SupervisorTeamParallelTest 做法)
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                AiDocument.class);
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiDocument>> captor =
                org.mockito.ArgumentCaptor.forClass(
                        (Class) com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        when(documentMapper.selectList(captor.capture())).thenReturn(List.of());

        tool.execute(Map.of("keyword", "目标"),
                com.forge.modules.ai.agent.tools.ToolExecutionContext.of(77L));
        String withUser = captor.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(withUser.contains("user_id"), "应含 user_id 条件: " + withUser);

        tool.execute(Map.of("keyword", "目标"),
                com.forge.modules.ai.agent.tools.ToolExecutionContext.of(0L));
        String withoutUser = captor.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertFalse(withoutUser.contains("user_id"), "无用户上下文不应含 user_id 条件");
    }

    @Test
    void 定时任务无用户全量检索_上下文归一() {
        when(documentMapper.selectList(any())).thenReturn(List.of(
                doc(1L, "他人文档.docx", null, "目标内容")));
        // userId=0 与 null 均归一为无限制
        Map<?, ?> byZero = (Map<?, ?>) tool.execute(Map.of("keyword", "目标"),
                com.forge.modules.ai.agent.tools.ToolExecutionContext.of(0L));
        Map<?, ?> byNull = (Map<?, ?>) tool.execute(Map.of("keyword", "目标"),
                com.forge.modules.ai.agent.tools.ToolExecutionContext.UNRESTRICTED);
        assertEquals(1, byZero.get("total"));
        assertEquals(1, byNull.get("total"));
    }

    @Test
    void 上下文语义判定() {
        var ctx = com.forge.modules.ai.agent.tools.ToolExecutionContext.of(77L);
        org.junit.jupiter.api.Assertions.assertTrue(ctx.hasUser());
        org.junit.jupiter.api.Assertions.assertTrue(ctx.limitedToUser(88L));
        org.junit.jupiter.api.Assertions.assertFalse(ctx.limitedToUser(77L));
        org.junit.jupiter.api.Assertions.assertFalse(
                com.forge.modules.ai.agent.tools.ToolExecutionContext.of(0L).hasUser());
    }

    private AiDocument doc(Long id, String fileName, String summary, String content) {
        AiDocument doc = new AiDocument();
        doc.setId(id);
        doc.setFileName(fileName);
        doc.setSummary(summary);
        doc.setContent(content);
        doc.setStatus(2);
        return doc;
    }
}

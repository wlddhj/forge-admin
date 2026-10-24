package com.forge.modules.ai.agent.tools;

import com.forge.modules.ai.agent.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SQL 工具字符预算单测：预算内完整返回、超预算裁剪并标注 truncated/omittedRows、列清单始终完整（AC9）
 */
class SqlQueryToolBudgetTest {

    private AgentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.setSqlMaxRows(200);
    }

    @Test
    void 预算内全部返回且不标注截断() throws Exception {
        properties.setSqlResultCharBudget(2000);
        SqlQueryTool spyTool = toolWithRows(3, "v");

        Object result = spyTool.execute(Map.of("sql", "select c from ai_document"));
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(3, map.get("rowCount"));
        assertFalse(Boolean.TRUE.equals(map.get("truncated")));
    }

    @Test
    void 超预算裁剪行集并标注() throws Exception {
        properties.setSqlResultCharBudget(120);
        properties.setSqlMaxRows(100);
        SqlQueryTool spyTool = toolWithRows(50, "长值内容".repeat(10));

        Map<?, ?> map = (Map<?, ?>) spyTool.execute(Map.of("sql", "select c from ai_document"));
        assertTrue(Boolean.TRUE.equals(map.get("truncated")));
        int rowCount = (Integer) map.get("rowCount");
        int omitted = (Integer) map.get("omittedRows");
        assertEquals(50, rowCount + omitted);
        assertTrue(rowCount < 50);
        // 列清单始终完整
        assertEquals(java.util.List.of("c"), map.get("columns"));
    }

    @Test
    void 零行结果不误标截断() throws Exception {
        properties.setSqlResultCharBudget(50);
        SqlQueryTool spyTool = toolWithRows(0, "v");

        Map<?, ?> map = (Map<?, ?>) spyTool.execute(Map.of("sql", "select c from ai_document"));
        assertEquals(0, map.get("rowCount"));
        assertFalse(Boolean.TRUE.equals(map.get("truncated")));
    }

    /**
     * 构造返回 n 行单列数据的工具（绕过租户改写器与真实数据源）
     */
    private SqlQueryTool toolWithRows(int rowCount, String cellValue) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        SqlTenantRewriter rewriter = mock(SqlTenantRewriter.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnLabel(1)).thenReturn("c");
        when(rewriter.rewrite(anyString())).thenAnswer(inv -> inv.getArgument(0));
        // 行迭代：前 rowCount 行 next=true，随后 false
        int[] remaining = {rowCount};
        when(rs.next()).thenAnswer(inv -> remaining[0]-- > 0);
        when(rs.getObject(1)).thenReturn(cellValue);
        return new SqlQueryTool(dataSource, properties, rewriter);
    }
}

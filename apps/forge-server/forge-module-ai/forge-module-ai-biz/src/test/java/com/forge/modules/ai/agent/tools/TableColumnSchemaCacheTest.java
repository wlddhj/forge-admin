package com.forge.modules.ai.agent.tools;

import com.forge.framework.localcache.LocalCaches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表列名缓存单测：TTL 内复用、探查失败 fail-open（空列不缓存，恢复后可重试）
 */
class TableColumnSchemaCacheTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet resultSet = mock(ResultSet.class);

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
    }

    private void stubColumns(String... columns) throws SQLException {
        AtomicInteger cursor = new AtomicInteger();
        when(resultSet.next()).thenAnswer(inv -> cursor.getAndIncrement() < columns.length);
        when(resultSet.getString(1)).thenAnswer(inv -> columns[cursor.get() - 1]);
    }

    @Test
    void cachesWithinTtlAndProbesOnlyOnce() throws Exception {
        TableColumnSchemaCache cache = new TableColumnSchemaCache(dataSource, new LocalCaches());
        stubColumns("id", "title", "create_time");

        List<String> first = cache.columnsOf("ai_chat_conversation");
        List<String> second = cache.columnsOf("ai_chat_conversation");

        assertThat(first).containsExactly("id", "title", "create_time");
        assertThat(second).isEqualTo(first);
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void blankTableReturnsEmptyWithoutProbe() throws Exception {
        TableColumnSchemaCache cache = new TableColumnSchemaCache(dataSource, new LocalCaches());
        assertThat(cache.columnsOf(null)).isEmpty();
        assertThat(cache.columnsOf("  ")).isEmpty();
        verify(dataSource, times(0)).getConnection();
    }

    @Test
    void probeFailureFailsOpenAndNotCached() throws Exception {
        TableColumnSchemaCache cache = new TableColumnSchemaCache(dataSource, new LocalCaches());
        org.mockito.Mockito.doThrow(new SQLException("db down")).when(dataSource).getConnection();

        // fail-open：失败返回空列，不抛异常
        assertThat(cache.columnsOf("t1")).isEmpty();

        // 失败不回填：恢复后重新探查成功
        org.mockito.Mockito.doReturn(connection).when(dataSource).getConnection();
        stubColumns("id");
        assertThat(cache.columnsOf("t1")).containsExactly("id");
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    void unknownTableCachesEmptyResult() throws Exception {
        TableColumnSchemaCache cache = new TableColumnSchemaCache(dataSource, new LocalCaches());
        stubColumns();

        assertThat(cache.columnsOf("nope")).isEmpty();
        assertThat(cache.columnsOf("nope")).isEmpty();
        // 不存在的表（0 行）也缓存，避免每次决策重复探查
        verify(dataSource, times(1)).getConnection();
    }
}

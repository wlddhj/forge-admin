package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import com.forge.framework.localcache.LocalCaches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantTableSchemaCacheTest {

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

    private void stubRows(String... tables) throws SQLException {
        AtomicInteger cursor = new AtomicInteger();
        when(resultSet.next()).thenAnswer(inv -> cursor.getAndIncrement() < tables.length);
        when(resultSet.getString(1)).thenAnswer(inv -> tables[cursor.get() - 1]);
    }

    @Test
    void cachesWithinTtlAndProbesOnlyOnce() throws Exception {
        TenantTableSchemaCache cache = new TenantTableSchemaCache(dataSource, new LocalCaches());
        stubRows("sys_user", "sys_order");

        Set<String> first = cache.getTenantTables();
        Set<String> second = cache.getTenantTables();

        assertThat(first).containsExactlyInAnyOrder("sys_user", "sys_order");
        assertThat(second).isEqualTo(first);
        // TTL 内仅探查一次（缓存复用）
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void probeFailureIsFailClosedAndNotCached() throws Exception {
        TenantTableSchemaCache cache = new TenantTableSchemaCache(dataSource, new LocalCaches());
        org.mockito.Mockito.doThrow(new SQLException("db down")).when(dataSource).getConnection();

        assertThatThrownBy(cache::getTenantTables).isInstanceOf(BusinessException.class);

        // 失败结果不回填（fail-closed 不缓存失败）：恢复后重新探查成功
        org.mockito.Mockito.doReturn(connection).when(dataSource).getConnection();
        stubRows("sys_user");
        assertThat(cache.getTenantTables()).containsExactly("sys_user");
        verify(dataSource, times(2)).getConnection();
    }
}

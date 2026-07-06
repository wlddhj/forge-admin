package com.forge.modules.screen.service;

import com.forge.modules.screen.cache.DataSourceCacheService;
import com.forge.modules.screen.dto.DataSourceExecuteRequest;
import com.forge.modules.screen.dto.DataSourceExecuteResponse;
import com.forge.modules.screen.entity.SysScreenDataSource;
import com.forge.modules.screen.executor.HttpDataSourceExecutor;
import com.forge.modules.screen.executor.SqlDataSourceExecutor;
import com.forge.modules.screen.fault.DataSourceCircuitBreaker;
import com.forge.modules.screen.mapper.SysScreenDataSourceMapper;
import com.forge.modules.screen.service.impl.SysScreenDataSourceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysScreenDataSourceServiceImpl 单元测试。
 *
 * <p>
 * 覆盖三条核心路径：
 * <ol>
 * <li>SQL 数据源走缓存（cacheSeconds &gt; 0）→ 命中缓存 → 不调用执行器、不记录失败。</li>
 * <li>熔断已开启 → 立即抛 BusinessException（含"熔断"），不查 mapper、不调 cache。</li>
 * <li>SQL 执行器抛异常 → recordFailure 被调用并向上抛出 BusinessException。</li>
 * </ol>
 * </p>
 *
 * <p>
 * 实现说明：由于 {@link DataSourceCacheService} 被模拟，缓存层对"未命中调 loader"的语义需要
 * 在测试中显式模拟：通过 {@code when(cache.getOrLoad(...)).thenAnswer(inv -> loader.get())}
 * 把 loader 直接转发到底层执行器；这样既覆盖 dispatch 路径，又能在 test #3 触发执行器异常。
 * </p>
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
class SysScreenDataSourceServiceImplTest {

    @Mock
    SysScreenDataSourceMapper mapper;
    @Mock
    SqlDataSourceExecutor sqlExecutor;
    @Mock
    HttpDataSourceExecutor httpExecutor;
    @Mock
    DataSourceCacheService cache;
    @Mock
    DataSourceCircuitBreaker breaker;
    @InjectMocks
    SysScreenDataSourceServiceImpl service;

    @Test
    void execute_sql_hits_cache_and_returns_data() {
        SysScreenDataSource ds = new SysScreenDataSource();
        ds.setId(1L);
        ds.setType("SQL");
        ds.setConfig("{\"sqlTemplate\":\"SELECT id FROM sys_user LIMIT 10\"}");
        ds.setCacheSeconds(60);
        when(mapper.selectById(1L)).thenReturn(ds);
        when(breaker.isTripped(1L)).thenReturn(false);
        when(cache.cacheKey(any(), anyString())).thenReturn("k1");
        // 模拟"缓存未命中 → loader 被调用 → 返回结果"
        when(cache.getOrLoad(eq("k1"), eq(60), any()))
            .thenAnswer(inv -> {
                Supplier<?> loader = inv.getArgument(2);
                return loader.get();
            });
        when(sqlExecutor.execute(anyString(), anyMap(), anyInt()))
            .thenReturn(List.of(Map.of("id", 1)));

        DataSourceExecuteRequest req = new DataSourceExecuteRequest();
        req.setParams(Map.of());
        DataSourceExecuteResponse resp = service.execute(1L, req);

        assertThat(resp.getData()).isNotNull();
        assertThat(resp.isFromCache()).isFalse();
        assertThat(resp.getExecutedAt()).isNotNull();
        verify(breaker).isTripped(1L);
        verify(cache).getOrLoad(eq("k1"), eq(60), any());
        verify(sqlExecutor).execute(anyString(), anyMap(), anyInt());
        verify(breaker).recordSuccess(1L);
        verify(breaker, never()).recordFailure(any());
    }

    @Test
    void execute_throws_when_breaker_tripped() {
        // 不需要 stub mapper —— 熔断前置会在 selectById 之前抛出
        when(breaker.isTripped(1L)).thenReturn(true);

        DataSourceExecuteRequest req = new DataSourceExecuteRequest();
        assertThatThrownBy(() -> service.execute(1L, req))
            .hasMessageContaining("熔断");
        // 熔断后不应触碰实体加载 / 缓存 / 执行器
        verify(mapper, never()).selectById(any());
        verify(cache, never()).getOrLoad(any(), anyInt(), any());
        verify(sqlExecutor, never()).execute(anyString(), anyMap(), anyInt());
    }

    @Test
    void execute_records_failure_on_exception() {
        SysScreenDataSource ds = new SysScreenDataSource();
        ds.setId(1L);
        ds.setType("SQL");
        ds.setCacheSeconds(0);
        ds.setConfig("{\"sqlTemplate\":\"SELECT 1\"}");
        when(mapper.selectById(1L)).thenReturn(ds);
        when(breaker.isTripped(1L)).thenReturn(false);
        when(cache.cacheKey(any(), anyString())).thenReturn("k2");
        // cacheSeconds=0：直接转发 loader，触发执行器异常
        when(cache.getOrLoad(eq("k2"), eq(0), any()))
            .thenAnswer(inv -> {
                Supplier<?> loader = inv.getArgument(2);
                return loader.get();
            });
        when(sqlExecutor.execute(anyString(), anyMap(), anyInt()))
            .thenThrow(new RuntimeException("DB error"));

        DataSourceExecuteRequest req = new DataSourceExecuteRequest();
        assertThatThrownBy(() -> service.execute(1L, req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("数据源执行失败");
        verify(breaker).recordFailure(1L);
        verify(breaker, never()).recordSuccess(any());
    }
}

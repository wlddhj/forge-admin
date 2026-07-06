package com.forge.modules.screen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.screen.dto.DataSourceExecuteRequest;
import com.forge.modules.screen.dto.DataSourceExecuteResponse;
import com.forge.modules.screen.dto.ScreenPageRequest;
import com.forge.modules.screen.entity.SysScreenDataSource;

import java.util.List;

/**
 * 大屏数据源管理服务。
 *
 * <p>
 * 职责：
 * <ul>
 * <li>CRUD：数据源元数据（编码、名称、类型、配置 JSON、缓存秒数、启用状态）。</li>
 * <li>{@link #execute(Long, DataSourceExecuteRequest)}：协调
 *     {@link com.forge.modules.screen.fault.DataSourceCircuitBreaker}（熔断）→
 *     {@link com.forge.modules.screen.cache.DataSourceCacheService}（缓存）→
 *     {@link com.forge.modules.screen.executor.SqlDataSourceExecutor}/{@code HttpDataSourceExecutor}
 *     （分发）三层流水线。</li>
 * </ul>
 * </p>
 *
 * @author standadmin
 */
public interface SysScreenDataSourceService {

    /**
     * 分页查询数据源。
     */
    Page<SysScreenDataSource> page(ScreenPageRequest request);

    /**
     * 根据 id 查询数据源；不存在抛 BusinessException。
     */
    SysScreenDataSource getById(Long id);

    /**
     * 新增数据源，返回生成的主键。
     */
    Long create(SysScreenDataSource entity);

    /**
     * 更新数据源。
     */
    void update(SysScreenDataSource entity);

    /**
     * 批量删除数据源。
     */
    void delete(List<Long> ids);

    /**
     * 执行数据源查询。
     *
     * <p>
     * 流程：熔断检查 → 加载实体 → 通过缓存层调度实际执行器（SQL/HTTP）→
     * 成功记录 breaker.recordSuccess，失败记录 breaker.recordFailure 并抛出 BusinessException。
     * </p>
     *
     * @param dataSourceId 数据源 id
     * @param request      执行请求（含参数 map）
     * @return 执行响应（数据、是否命中缓存、执行时间）
     */
    DataSourceExecuteResponse execute(Long dataSourceId, DataSourceExecuteRequest request);
}

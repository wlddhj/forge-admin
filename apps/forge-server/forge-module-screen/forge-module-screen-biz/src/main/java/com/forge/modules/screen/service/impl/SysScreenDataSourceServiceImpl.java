package com.forge.modules.screen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.screen.cache.DataSourceCacheService;
import com.forge.modules.screen.constant.ScreenConstants;
import com.forge.modules.screen.dto.DataSourceExecuteRequest;
import com.forge.modules.screen.dto.DataSourceExecuteResponse;
import com.forge.modules.screen.dto.ScreenPageRequest;
import com.forge.modules.screen.entity.SysScreenDataSource;
import com.forge.modules.screen.enums.DataSourceType;
import com.forge.modules.screen.executor.HttpDataSourceExecutor;
import com.forge.modules.screen.executor.SqlDataSourceExecutor;
import com.forge.modules.screen.fault.DataSourceCircuitBreaker;
import com.forge.modules.screen.mapper.SysScreenDataSourceMapper;
import com.forge.modules.screen.service.SysScreenDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 大屏数据源管理服务实现：CRUD + execute 协调器。
 *
 * <h3>execute 协调器职责</h3>
 * <ol>
 *   <li><b>熔断前置</b>：先查 {@link DataSourceCircuitBreaker#isTripped(Long)}，
 *       若已熔断立即抛 BusinessException，避免继续打死下游。</li>
 *   <li><b>实体加载</b>：通过 {@link #getById(Long)} 取得数据源配置（含 type / config / cacheSeconds）。</li>
 *   <li><b>缓存调度</b>：以 {@code dataSourceId + paramsJson} 生成缓存 key，
 *       委托 {@link DataSourceCacheService#getOrLoad} 决定是否命中 Redis 缓存或调用 loader。</li>
 *   <li><b>分发执行</b>：根据 {@link DataSourceType} 选择
 *       {@link SqlDataSourceExecutor} 或 {@link HttpDataSourceExecutor}。</li>
 *   <li><b>熔断反馈</b>：成功调 {@code recordSuccess} 清计数；失败调 {@code recordFailure}
 *       累计并可能在阈值处触发熔断；异常向上包成 BusinessException。</li>
 * </ol>
 *
 * <h3>已知偏差</h3>
 * <ul>
 *   <li>{@code fromCache} 字段当前固定返回 {@code false}：DataSourceCacheService 当前接口
 *       不暴露"是否命中"信号。如需精确告知前端，需要在 T13 上增加返回结构或此处自行先查 Redis。
 *       本任务范围保持现状（测试也据此编写）。</li>
 *   <li>ObjectMapper 实例字段：本类持有自管理 ObjectMapper。若后续需要注入统一实例，
 *       可改为构造函数注入；当前不影响功能。</li>
 * </ul>
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysScreenDataSourceServiceImpl implements SysScreenDataSourceService {

    private final SysScreenDataSourceMapper mapper;
    private final SqlDataSourceExecutor sqlExecutor;
    private final HttpDataSourceExecutor httpExecutor;
    private final DataSourceCacheService cache;
    private final DataSourceCircuitBreaker breaker;
    /** 自管理 ObjectMapper；线程安全可共享。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Page<SysScreenDataSource> page(ScreenPageRequest request) {
        Page<SysScreenDataSource> p = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysScreenDataSource> qw = new LambdaQueryWrapper<>();
        qw.select(SysScreenDataSource.class, info -> !"config".equals(info.getColumn()));
        if (request.getName() != null && !request.getName().isBlank()) {
            qw.like(SysScreenDataSource::getName, request.getName());
        }
        qw.orderByDesc(SysScreenDataSource::getUpdateTime);
        return mapper.selectPage(p, qw);
    }

    @Override
    public SysScreenDataSource getById(Long id) {
        SysScreenDataSource ds = mapper.selectById(id);
        if (ds == null) {
            throw new BusinessException("数据源不存在");
        }
        return ds;
    }

    @Override
    @Transactional
    public Long create(SysScreenDataSource entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(SysScreenDataSource entity) {
        mapper.updateById(entity);
    }

    @Override
    @Transactional
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        mapper.deleteBatchIds(ids);
    }

    @Override
    public DataSourceExecuteResponse execute(Long dataSourceId, DataSourceExecuteRequest request) {
        // ① 熔断前置：失败窗口已开启时立即拒绝，避免继续打死下游
        if (breaker.isTripped(dataSourceId)) {
            throw new BusinessException("数据源已熔断，请稍后重试");
        }

        SysScreenDataSource ds = getById(dataSourceId);
        Map<String, Object> params = (request == null || request.getParams() == null)
            ? Map.of() : request.getParams();

        try {
            String paramsJson = toJson(params);
            String cacheKey = cache.cacheKey(dataSourceId, paramsJson);
            Object data = cache.getOrLoad(cacheKey, ds.getCacheSeconds(),
                () -> dispatch(ds, params));
            breaker.recordSuccess(dataSourceId);
            return new DataSourceExecuteResponse(data, false, LocalDateTime.now());
        } catch (Exception e) {
            breaker.recordFailure(dataSourceId);
            log.error("数据源执行失败 id={}", dataSourceId, e);
            // 已是 BusinessException 时直接向上抛，避免双层包装
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException("数据源执行失败: " + e.getMessage());
        }
    }

    /**
     * 根据数据源类型分发到 SQL / HTTP 执行器。
     *
     * @throws BusinessException 未知类型 / config 解析失败 / 执行器抛出非 BusinessException 时
     *                          由 {@link #execute} 包装为 BusinessException
     */
    private Object dispatch(SysScreenDataSource ds, Map<String, Object> params) {
        DataSourceType type;
        try {
            type = DataSourceType.valueOf(ds.getType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("未知数据源类型: " + ds.getType());
        }

        if (type == DataSourceType.SQL) {
            Map<String, Object> cfg = parseConfig(ds.getConfig());
            Object sqlTemplate = cfg.get("sqlTemplate");
            if (!(sqlTemplate instanceof String sql) || sql.isBlank()) {
                throw new BusinessException("SQL 数据源缺少 sqlTemplate");
            }
            return sqlExecutor.execute(sql, params, ScreenConstants.SQL_TIMEOUT_MS);
        }
        if (type == DataSourceType.HTTP) {
            Map<String, Object> cfg = parseConfig(ds.getConfig());
            return httpExecutor.execute(cfg, params);
        }
        throw new BusinessException("未知数据源类型: " + ds.getType());
    }

    /**
     * 解析数据源 config（JSON 字符串）为 Map。
     *
     * @throws BusinessException JSON 解析失败
     */
    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException("数据源 config 为空");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("数据源 config 解析失败");
        }
    }

    /**
     * 序列化 params 为 JSON，用于缓存 key 生成；失败时返回空串以保证 cacheKey 仍可生成。
     */
    private String toJson(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            log.warn("params 序列化失败，使用空串作为缓存 key 输入; err={}", e.getMessage());
            return "";
        }
    }
}

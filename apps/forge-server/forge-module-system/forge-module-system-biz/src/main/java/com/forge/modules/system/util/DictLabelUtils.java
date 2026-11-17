package com.forge.modules.system.util;

import com.forge.modules.system.dto.dict.DictDataResponse;
import com.forge.modules.system.service.SysDictDataService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用数据字典转换工具（码值 → label）——后端码值渲染统一出口，静态调用。
 * <p>
 * 三级链路：本地 Guava 缓存（60s TTL，O(1) 查找）→ Redis（SysDictDataService 的
 * {@code @Cacheable("dictData")}，1h TTL）→ DB。同一 dictType 在 TTL 窗口内整页
 * 所有码值只加载一次（按 type 缓存整张 value→label 映射），并发未命中由 Guava
 * 同 key in-flight 去重，防缓存击穿。
 * <p>
 * 字典数据为跨租户共享配置（dictData 在 CacheConfig ignoreCaches 中），缓存 key
 * 无租户维度。字典写操作由 SysDictDataServiceImpl 调用 {@link #evictLocalCache()}
 * 即时失效本实例缓存；多实例部署下其他实例靠 60s TTL 收敛（脏窗 ≤60s）。
 * <p>
 * 字典查询只含启用项（SysDictDataService.getDictDataByType 已在 SQL 层过滤），
 * 异常不外抛——字典侧故障只降级为未命中，不阻断业务。后端任何模块不要在业务
 * 代码里各自硬编码 switch/if 映射，统一走本工具。
 *
 * @author forge-admin
 */
@Slf4j
public class DictLabelUtils {

    /** 本地缓存容量上限：字典类型数远小于此量级，防误用兜底 */
    private static final long CACHE_MAXIMUM_SIZE = 256;

    /** 本地缓存 TTL：跨实例脏窗上限（本实例写路径有即时 evict 钩子兜底） */
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    /**
     * init 注入的是带 @Cacheable 代理的 Bean（见 DictLabelInitializer），
     * 字典加载经代理走 Redis 二级缓存，本地未命中不会直连 DB
     */
    private static SysDictDataService dictDataService;

    /** type → (value → label) 不可变映射；空映射作为"字典未配置"的本地负缓存 */
    private static final Cache<String, Map<String, String>> LOCAL_CACHE = CacheBuilder.newBuilder()
            .maximumSize(CACHE_MAXIMUM_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .build();

    private DictLabelUtils() {
    }

    /**
     * 注入字典服务，由 DictLabelInitializer 在容器启动时调用。
     * 不能改在 SysDictDataServiceImpl 内 init(this)——@PostConstruct 时机 Bean
     * 尚未代理，静态调用会绕过 Redis 缓存直连 DB。
     */
    public static void init(SysDictDataService service) {
        dictDataService = service;
        log.info("[init][初始化 DictLabelUtils 成功]");
    }

    /**
     * 按字典类型与码值解析 label（本地缓存命中时 O(1)，无网络开销）
     *
     * @param dictType 字典类型（如 contract_status、demo_project_status）
     * @param value    码值（数字/字符串均可，按字符串比较）
     * @return 启用项的 label；码值为空、字典未配置或未命中返回 null（调用方自行回退）
     */
    public static String resolve(String dictType, Object value) {
        if (dictType == null || dictType.isBlank() || value == null) {
            return null;
        }
        return cachedMapping(dictType).get(String.valueOf(value));
    }

    /**
     * 带回退的解析：未命中（或字典异常）返回 defaultValue
     */
    public static String resolveOrDefault(String dictType, Object value, String defaultValue) {
        String label = resolve(dictType, value);
        return label != null ? label : defaultValue;
    }

    /**
     * 整字典批量解析（value → label 映射）：列表页组装用，一页一次替代逐行 resolve，
     * 与 {@link #resolve} 共用同一份本地缓存。
     *
     * @return 启用项的不可变映射（共享实例，勿修改）；字典为空/异常返回空 Map（永不为 null）
     */
    public static Map<String, String> resolveMap(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return Map.of();
        }
        return cachedMapping(dictType);
    }

    /**
     * 清空本地缓存——字典写路径（增删改/状态切换/批量保存/手动刷新）的即时失效入口。
     * <p>
     * evict 发生在写事务提交前，理论上有"并发线程回填旧值"的微竞态（ms 级窗口，
     * 与既有 @CacheEvict 同在），由 60s TTL 兜底收敛；多实例部署下其他实例同样靠 TTL。
     */
    public static void evictLocalCache() {
        LOCAL_CACHE.invalidateAll();
    }

    /**
     * 取 type → (value → label) 映射：本地未命中时经 SysDictDataService 加载（走
     * Redis 二级缓存），同 key 并发加载由 Guava in-flight 去重。加载失败（含 init
     * 未完成）降级为空 Map 且不缓存，下次调用自动重试。
     */
    private static Map<String, String> cachedMapping(String dictType) {
        try {
            return LOCAL_CACHE.get(dictType, () -> loadMapping(dictType));
        } catch (Exception e) {
            log.warn("字典 label 解析失败，按未命中处理: dictType={}", dictType, e);
            return Map.of();
        }
    }

    private static Map<String, String> loadMapping(String dictType) {
        List<DictDataResponse> dictData = dictDataService.getDictDataByType(dictType);
        if (dictData == null || dictData.isEmpty()) {
            // 空结果本地负缓存：Redis 层不缓存空列表（unless isEmpty），由本地挡住回源
            return Map.of();
        }
        Map<String, String> mapping = new HashMap<>();
        for (DictDataResponse d : dictData) {
            // 同码值以 dictSort 靠前者优先（服务端已按 dictSort 升序返回）
            if (d.getDictValue() != null && d.getDictLabel() != null) {
                mapping.putIfAbsent(d.getDictValue(), d.getDictLabel());
            }
        }
        return Collections.unmodifiableMap(mapping);
    }
}

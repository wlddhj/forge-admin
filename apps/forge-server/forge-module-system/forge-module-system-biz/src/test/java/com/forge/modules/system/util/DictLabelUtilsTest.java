package com.forge.modules.system.util;

import com.forge.modules.system.dto.dict.DictDataResponse;
import com.forge.modules.system.service.SysDictDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通用字典转换工具单元测试（命中/未命中/回退/容错 + 本地缓存行为）
 */
class DictLabelUtilsTest {

    private SysDictDataService dictDataService;

    @BeforeEach
    void setUp() {
        dictDataService = mock(SysDictDataService.class);
        // 静态缓存跨用例共享：每例重置服务引用并清空本地缓存
        DictLabelUtils.init(dictDataService);
        DictLabelUtils.evictLocalCache();
    }

    private DictDataResponse data(String label, String value) {
        DictDataResponse d = new DictDataResponse();
        d.setDictLabel(label);
        d.setDictValue(value);
        return d;
    }

    @Test
    @DisplayName("码值命中返回 label（数字码值按字符串比较）")
    void resolveHit() {
        when(dictDataService.getDictDataByType("contract_status"))
                .thenReturn(List.of(data("草稿", "0"), data("审批中", "1")));
        assertEquals("审批中", DictLabelUtils.resolve("contract_status", 1));
        assertEquals("审批中", DictLabelUtils.resolve("contract_status", "1"));
    }

    @Test
    @DisplayName("未命中返回 null；resolveOrDefault 回退默认值")
    void resolveMissAndFallback() {
        when(dictDataService.getDictDataByType("contract_status")).thenReturn(List.of(data("草稿", "0")));
        assertNull(DictLabelUtils.resolve("contract_status", 9));
        assertEquals("9", DictLabelUtils.resolveOrDefault("contract_status", 9, "9"));
    }

    @Test
    @DisplayName("码值/类型为空直接返回 null，不触发字典查询")
    void nullArguments() {
        assertNull(DictLabelUtils.resolve(null, 1));
        assertNull(DictLabelUtils.resolve("  ", 1));
        assertNull(DictLabelUtils.resolve("contract_status", null));
        verify(dictDataService, never()).getDictDataByType(anyString());
    }

    @Test
    @DisplayName("字典服务异常降级为未命中，不外抛")
    void dictServiceFailureDegrades() {
        when(dictDataService.getDictDataByType(anyString())).thenThrow(new IllegalStateException("db down"));
        assertNull(DictLabelUtils.resolve("contract_status", 1));
        assertEquals("1", DictLabelUtils.resolveOrDefault("contract_status", 1, "1"));
    }

    @Test
    @DisplayName("字典类型未配置（返回空列表）按未命中处理")
    void emptyDictType() {
        when(dictDataService.getDictDataByType("nosuch")).thenReturn(List.of());
        assertNull(DictLabelUtils.resolve("nosuch", 1));
    }

    @Test
    @DisplayName("resolveMap 批量解析：value→label 映射，供列表页级一次查询")
    void resolveMapBatch() {
        when(dictDataService.getDictDataByType("demo_project_status"))
                .thenReturn(List.of(data("审批中", "APPROVING"), data("已通过", "APPROVED")));
        var mapping = DictLabelUtils.resolveMap("demo_project_status");
        assertEquals("审批中", mapping.get("APPROVING"));
        assertEquals(2, mapping.size());
    }

    @Test
    @DisplayName("resolveMap 异常/空参返回空 Map（永不为 null）")
    void resolveMapDegrades() {
        assertTrue(DictLabelUtils.resolveMap(null).isEmpty());
        assertTrue(DictLabelUtils.resolveMap("  ").isEmpty());
        when(dictDataService.getDictDataByType(anyString())).thenThrow(new IllegalStateException("db down"));
        assertTrue(DictLabelUtils.resolveMap("demo_project_status").isEmpty());
    }

    @Test
    @DisplayName("本地缓存命中：重复 resolve 同一 type 只触发一次字典服务调用")
    void repeatResolveHitsServiceOnce() {
        when(dictDataService.getDictDataByType("contract_status")).thenReturn(List.of(data("草稿", "0")));
        assertEquals("草稿", DictLabelUtils.resolve("contract_status", 0));
        assertEquals("草稿", DictLabelUtils.resolve("contract_status", "0"));
        assertEquals("草稿", DictLabelUtils.resolveMap("contract_status").get("0"));
        verify(dictDataService, times(1)).getDictDataByType("contract_status");
    }

    @Test
    @DisplayName("evictLocalCache 后重新加载：字典写路径失效立即生效")
    void evictLocalCacheReloads() {
        when(dictDataService.getDictDataByType("contract_status")).thenReturn(List.of(data("草稿", "0")));
        assertEquals("草稿", DictLabelUtils.resolve("contract_status", 0));
        DictLabelUtils.evictLocalCache();
        assertEquals("草稿", DictLabelUtils.resolve("contract_status", 0));
        verify(dictDataService, times(2)).getDictDataByType("contract_status");
    }

    @Test
    @DisplayName("空字典本地负缓存：TTL 窗口内重复解析不再回源")
    void emptyDictNegativelyCached() {
        when(dictDataService.getDictDataByType("nosuch")).thenReturn(List.of());
        assertNull(DictLabelUtils.resolve("nosuch", 1));
        assertNull(DictLabelUtils.resolve("nosuch", 1));
        verify(dictDataService, times(1)).getDictDataByType("nosuch");
    }

    @Test
    @DisplayName("加载失败不缓存：下次调用自动重试并恢复")
    void failureNotCached() {
        when(dictDataService.getDictDataByType("contract_status")).thenThrow(new IllegalStateException("db down"));
        assertNull(DictLabelUtils.resolve("contract_status", 0));
        // 旧桩是 thenThrow，重打桩必须用 doReturn 族：when(mock.x()) 的参数求值会触发旧桩直接抛
        doReturn(List.of(data("草稿", "0"))).when(dictDataService).getDictDataByType("contract_status");
        assertEquals("草稿", DictLabelUtils.resolve("contract_status", 0));
        verify(dictDataService, times(2)).getDictDataByType("contract_status");
    }

    @Test
    @DisplayName("resolveMap 返回不可变映射：写入抛 UnsupportedOperationException")
    void resolveMapImmutable() {
        when(dictDataService.getDictDataByType("demo_project_status"))
                .thenReturn(List.of(data("审批中", "APPROVING")));
        var mapping = DictLabelUtils.resolveMap("demo_project_status");
        assertThrows(UnsupportedOperationException.class, () -> mapping.put("X", "y"));
    }
}

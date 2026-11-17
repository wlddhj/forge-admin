package com.forge.modules.system.service;

import com.forge.modules.system.dto.dict.DictDataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通用字典转换工具单元测试（命中/未命中/回退/容错）
 */
class DictLabelResolverTest {

    private SysDictDataService dictDataService;
    private DictLabelResolver resolver;

    @BeforeEach
    void setUp() {
        dictDataService = mock(SysDictDataService.class);
        resolver = new DictLabelResolver(dictDataService);
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
        assertEquals("审批中", resolver.resolve("contract_status", 1));
        assertEquals("审批中", resolver.resolve("contract_status", "1"));
    }

    @Test
    @DisplayName("未命中返回 null；resolveOrDefault 回退默认值")
    void resolveMissAndFallback() {
        when(dictDataService.getDictDataByType("contract_status")).thenReturn(List.of(data("草稿", "0")));
        assertNull(resolver.resolve("contract_status", 9));
        assertEquals("9", resolver.resolveOrDefault("contract_status", 9, "9"));
    }

    @Test
    @DisplayName("码值/类型为空直接返回 null，不触发字典查询")
    void nullArguments() {
        assertNull(resolver.resolve(null, 1));
        assertNull(resolver.resolve("  ", 1));
        assertNull(resolver.resolve("contract_status", null));
    }

    @Test
    @DisplayName("字典服务异常降级为未命中，不外抛")
    void dictServiceFailureDegrades() {
        when(dictDataService.getDictDataByType(anyString())).thenThrow(new IllegalStateException("db down"));
        assertNull(resolver.resolve("contract_status", 1));
        assertEquals("1", resolver.resolveOrDefault("contract_status", 1, "1"));
    }

    @Test
    @DisplayName("字典类型未配置（返回空列表）按未命中处理")
    void emptyDictType() {
        when(dictDataService.getDictDataByType("nosuch")).thenReturn(List.of());
        assertNull(resolver.resolve("nosuch", 1));
    }

    @Test
    @DisplayName("resolveMap 批量解析：value→label 映射，供列表页级一次查询")
    void resolveMapBatch() {
        when(dictDataService.getDictDataByType("demo_project_status"))
                .thenReturn(List.of(data("审批中", "APPROVING"), data("已通过", "APPROVED")));
        var mapping = resolver.resolveMap("demo_project_status");
        assertEquals("审批中", mapping.get("APPROVING"));
        assertEquals(2, mapping.size());
    }

    @Test
    @DisplayName("resolveMap 异常/空参返回空 Map（永不为 null）")
    void resolveMapDegrades() {
        assertTrue(resolver.resolveMap(null).isEmpty());
        assertTrue(resolver.resolveMap("  ").isEmpty());
        when(dictDataService.getDictDataByType(anyString())).thenThrow(new IllegalStateException("db down"));
        assertTrue(resolver.resolveMap("demo_project_status").isEmpty());
    }
}

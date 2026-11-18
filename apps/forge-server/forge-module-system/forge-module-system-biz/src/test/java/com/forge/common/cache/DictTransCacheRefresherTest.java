package com.forge.common.cache;

import com.forge.modules.system.dto.dict.DictDataResponse;
import com.forge.modules.system.dto.dict.DictTypeResponse;
import com.forge.modules.system.service.SysDictDataService;
import com.forge.modules.system.service.SysDictTypeService;
import org.dromara.trans.service.impl.DictionaryTransService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DictTransCacheRefresher 单测：组装语义（putIfAbsent/跳过空值）、空白与去重、
 * afterCommit 时序（有事务延迟/无事务立即）、remove 移除组、refreshAll 枚举、单类型失败隔离
 */
class DictTransCacheRefresherTest {

    private SysDictDataService dictDataService;
    private SysDictTypeService dictTypeService;
    private DictionaryTransService dictionaryTransService;
    private DictTransCacheRefresher refresher;

    @BeforeEach
    void setUp() {
        dictDataService = mock(SysDictDataService.class);
        dictTypeService = mock(SysDictTypeService.class);
        dictionaryTransService = mock(DictionaryTransService.class);
        refresher = new DictTransCacheRefresher(dictDataService, dictTypeService, dictionaryTransService);
    }

    @AfterEach
    void tearDown() {
        // 防 afterCommit 用例异常残留 ThreadLocal 污染同线程后续用例
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private DictDataResponse data(String value, String label) {
        DictDataResponse d = new DictDataResponse();
        d.setDictValue(value);
        d.setDictLabel(label);
        return d;
    }

    @Test
    void refresh_noTransaction_runsImmediately() {
        when(dictDataService.getDictDataByType("t")).thenReturn(List.of(data("1", "启用"), data("0", "禁用")));

        refresher.refreshTypesAfterCommit("t");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dictionaryTransService).refreshCacheAndNoticeOtherService(eq("t"), captor.capture());
        assertThat(captor.getValue()).containsEntry("1", "启用").containsEntry("0", "禁用");
    }

    @Test
    void refresh_mapAssembly_putIfAbsentAndSkipNull() {
        // dictSort 升序返回：同码值首见（靠前）优先；null value/label 跳过
        when(dictDataService.getDictDataByType("t")).thenReturn(List.of(
                data("1", "靠前label"), data("1", "靠后label"), data("x", null), data(null, "无码值")));

        refresher.refreshTypesAfterCommit("t");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dictionaryTransService).refreshCacheAndNoticeOtherService(eq("t"), captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("1");
        assertThat(captor.getValue()).containsEntry("1", "靠前label");
    }

    @Test
    void refresh_blankTypesIgnoredAndDuplicatesDeduped() {
        refresher.refreshTypesAfterCommit(" ", "", null);
        verify(dictionaryTransService, never()).refreshCacheAndNoticeOtherService(anyString(), any());

        when(dictDataService.getDictDataByType("t")).thenReturn(List.of());
        refresher.refreshTypesAfterCommit("t", "t");
        verify(dictionaryTransService, times(1)).refreshCacheAndNoticeOtherService(eq("t"), any());
    }

    @Test
    void refresh_emptyDictRefreshedAsEmptyMap_negativeSemantics() {
        // 全停用/未配置 → 空 Map 重建（翻译未命中返回 null 的负语义），不是跳过
        when(dictDataService.getDictDataByType("t")).thenReturn(List.of());

        refresher.refreshTypesAfterCommit("t");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dictionaryTransService).refreshCacheAndNoticeOtherService(eq("t"), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void refresh_withActiveTransaction_deferredUntilAfterCommit() {
        when(dictDataService.getDictDataByType("t")).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        try {
            refresher.refreshTypesAfterCommit("t");
            // 事务未提交前不触发
            verify(dictionaryTransService, never()).refreshCacheAndNoticeOtherService(anyString(), any());

            // 模拟事务提交后的 afterCommit 阶段
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(dictionaryTransService).refreshCacheAndNoticeOtherService(eq("t"), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void refreshOneFailure_doesNotBlockOtherTypes() {
        when(dictDataService.getDictDataByType("bad")).thenThrow(new RuntimeException("db down"));
        when(dictDataService.getDictDataByType("good")).thenReturn(List.of(data("1", "ok")));

        refresher.refreshTypesAfterCommit("bad", "good");

        verify(dictionaryTransService, never()).refreshCacheAndNoticeOtherService(eq("bad"), any());
        verify(dictionaryTransService).refreshCacheAndNoticeOtherService(eq("good"), any());
    }

    @Test
    void removeType_removesGroupAndNoticesOthers() {
        refresher.removeTypeAfterCommit("gone");
        verify(dictionaryTransService).removeDictGroupAndNoticeOtherService("gone");

        refresher.removeTypeAfterCommit("  ");
        verify(dictionaryTransService, times(1)).removeDictGroupAndNoticeOtherService(anyString());
    }

    @Test
    void removeType_failureSwallowed_notThrown() {
        doThrow(new RuntimeException("redis down")).when(dictionaryTransService).removeDictGroupAndNoticeOtherService("gone");

        // 异常只记日志不外抛（afterCommit 回调语义）
        refresher.removeTypeAfterCommit("gone");
    }

    @Test
    void refreshAll_enumeratesAllEnabledTypes() {
        DictTypeResponse t1 = new DictTypeResponse();
        t1.setDictType("a");
        DictTypeResponse t2 = new DictTypeResponse();
        t2.setDictType("b");
        when(dictTypeService.getAllDictTypes()).thenReturn(List.of(t1, t2));
        when(dictDataService.getDictDataByType(anyString())).thenReturn(List.of());

        refresher.refreshAll();

        verify(dictionaryTransService).refreshCacheAndNoticeOtherService(eq("a"), any());
        verify(dictionaryTransService).refreshCacheAndNoticeOtherService(eq("b"), any());
    }

    @Test
    void refreshAll_enumerationFailureSwallowed_notThrown() {
        // 启动期 DB 未就绪等场景：枚举失败不外抛，不阻断 ApplicationReadyEvent
        when(dictTypeService.getAllDictTypes()).thenThrow(new RuntimeException("db not ready"));

        refresher.refreshAll();

        verify(dictionaryTransService, never()).refreshCacheAndNoticeOtherService(anyString(), any());
    }
}

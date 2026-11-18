package com.forge.modules.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.forge.common.cache.DictTransCacheRefresher;
import com.forge.modules.system.dto.dict.DictDataRequest;
import com.forge.modules.system.entity.SysDictData;
import com.forge.modules.system.mapper.SysDictDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SysDictDataServiceImpl 字典写路径 → easy-trans DICTIONARY 翻译缓存刷新钩子矩阵：
 * 增/改（类型未变与变更）/删（前置收集类型）/停启/批量保存/手动刷新
 */
class SysDictDataServiceImplTest {

    private SysDictDataMapper mapper;
    private DictTransCacheRefresher refresher;
    private SysDictDataServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(SysDictDataMapper.class);
        refresher = mock(DictTransCacheRefresher.class);
        service = spy(new SysDictDataServiceImpl(mapper, refresher));
    }

    private SysDictData entity(Long id, String dictType, String value) {
        SysDictData d = new SysDictData();
        d.setId(id);
        d.setDictType(dictType);
        d.setDictValue(value);
        return d;
    }

    private DictDataRequest request(Long id, String dictType, String value) {
        DictDataRequest r = new DictDataRequest();
        r.setId(id);
        r.setDictType(dictType);
        r.setDictValue(value);
        return r;
    }

    @Test
    void addDictData_refreshesType() {
        doReturn(true).when(service).save(any(SysDictData.class));

        service.addDictData(request(null, "t1", "1"));

        verify(refresher).refreshTypesAfterCommit("t1");
    }

    @Test
    void updateDictData_sameType_refreshesOnce() {
        doReturn(entity(10L, "t1", "1")).when(service).getById(10L);
        doReturn(true).when(service).updateById(any(SysDictData.class));

        service.updateDictData(request(10L, "t1", "2"));

        // 实现恒传旧/新两参，由 refresher 内部去重保证单次刷新
        verify(refresher).refreshTypesAfterCommit("t1", "t1");
    }

    @Test
    void updateDictData_typeChanged_refreshesOldAndNew() {
        doReturn(entity(10L, "old", "1")).when(service).getById(10L);
        doReturn(true).when(service).updateById(any(SysDictData.class));

        service.updateDictData(request(10L, "new", "1"));

        verify(refresher).refreshTypesAfterCommit("old", "new");
    }

    @Test
    void deleteDictData_collectsTypesBeforeRemove() {
        doReturn(List.of(entity(1L, "a", "1"), entity(2L, "b", "1"), entity(3L, "a", "2")))
                .when(service).listByIds(anyList());
        doReturn(true).when(service).removeByIds(anyList());

        service.deleteDictData(List.of(1L, 2L, 3L));

        verify(service).removeByIds(anyList());
        // 同类型去重：a、b
        verify(refresher).refreshTypesAfterCommit("a", "b");
    }

    @Test
    void updateStatus_refreshesType() {
        doReturn(entity(10L, "t1", "1")).when(service).getById(10L);
        doReturn(true).when(service).updateById(any(SysDictData.class));

        service.updateStatus(10L, 0);

        verify(refresher).refreshTypesAfterCommit("t1");
    }

    @Test
    void refreshCache_rebuildsAll() {
        service.refreshCache();

        verify(refresher).refreshAll();
    }

    @Test
    void batchSaveDictData_refreshesBatchType() {
        // 既有 id=10/20；提交：更新 10、新增无 id；20 未提交 → 被删
        LambdaQueryChainWrapper<SysDictData> chain = mock(LambdaQueryChainWrapper.class);
        doReturn(chain).when(service).lambdaQuery();
        doReturn(chain).when(chain).eq(any(SFunction.class), any());
        doReturn(chain).when(chain).select(any(SFunction.class));
        doReturn(List.of(entity(10L, "t1", "1"), entity(20L, "t1", "2"))).when(chain).list();

        doReturn(entity(10L, "t1", "1")).when(service).getById(10L);
        doReturn(true).when(service).updateById(any(SysDictData.class));
        doReturn(true).when(service).save(any(SysDictData.class));
        doReturn(true).when(service).removeByIds(anyList());

        service.batchSaveDictData(new com.forge.modules.system.dto.dict.DictDataBatchSaveRequest() {{
            setDictType("t1");
            setDataList(List.of(request(10L, null, "1x"), request(null, null, "9")));
        }});

        verify(service).removeByIds(List.of(20L));
        verify(refresher).refreshTypesAfterCommit("t1");
    }

    @Test
    void readPath_neverTouchesRefresher() {
        LambdaQueryChainWrapper<SysDictData> chain = mock(LambdaQueryChainWrapper.class);
        doReturn(chain).when(service).lambdaQuery();
        doReturn(chain).when(chain).eq(any(SFunction.class), any());
        doReturn(chain).when(chain).orderByAsc(any(SFunction.class));
        doReturn(List.of()).when(chain).list();

        service.getDictDataByType("t1");
        verifyNoInteractions(refresher);

        verify(refresher, never()).refreshAll();
    }

    @Test
    void deleteDictData_keepsOrderingWithDistinctTypes() {
        // 参数顺序无关紧要，但须与前置查询一致——以 eq("t1") 调用防误桩验证链路
        doReturn(List.of(entity(1L, "only", "1"))).when(service).listByIds(anyList());
        doReturn(true).when(service).removeByIds(anyList());

        service.deleteDictData(List.of(1L));

        verify(refresher).refreshTypesAfterCommit(eq("only"));
    }
}

package com.forge.modules.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.forge.common.cache.DictTransCacheRefresher;
import com.forge.modules.system.dto.dict.DictTypeRequest;
import com.forge.modules.system.entity.SysDictType;
import com.forge.modules.system.mapper.SysDictDataMapper;
import com.forge.modules.system.mapper.SysDictTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SysDictTypeServiceImpl 字典类型写路径 → easy-trans DICTIONARY 翻译缓存钩子：
 * 删除移除组、编码变更移旧刷新新、未改名与新增不触碰
 */
class SysDictTypeServiceImplTest {

    private SysDictTypeMapper typeMapper;
    private SysDictDataMapper dataMapper;
    private DictTransCacheRefresher refresher;
    private SysDictTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        typeMapper = mock(SysDictTypeMapper.class);
        dataMapper = mock(SysDictDataMapper.class);
        refresher = mock(DictTransCacheRefresher.class);
        service = spy(new SysDictTypeServiceImpl(typeMapper, dataMapper, refresher));
    }

    private SysDictType entity(Long id, String dictType) {
        SysDictType t = new SysDictType();
        t.setId(id);
        t.setDictType(dictType);
        return t;
    }

    private DictTypeRequest request(Long id, String dictType) {
        DictTypeRequest r = new DictTypeRequest();
        r.setId(id);
        r.setDictType(dictType);
        return r;
    }

    @Test
    void deleteDictTypes_removesGroupsBeforeDelete() {
        doReturn(List.of(entity(1L, "a"), entity(2L, "b"), entity(3L, "a")))
                .when(service).listByIds(anyList());
        doReturn(true).when(service).removeByIds(anyList());

        service.deleteDictTypes(List.of(1L, 2L, 3L));

        verify(service).removeByIds(anyList());
        // 同类型去重：a、b 各移除一次
        verify(refresher).removeTypeAfterCommit("a");
        verify(refresher).removeTypeAfterCommit("b");
    }

    @Test
    void updateDictType_renamed_removesOldAndRefreshesNew() {
        doReturn(entity(10L, "old")).when(service).getById(10L);
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<SysDictType> chain = mock(LambdaQueryChainWrapper.class);
        doReturn(chain).when(service).lambdaQuery();
        doReturn(chain).when(chain).eq(any(SFunction.class), any());
        doReturn(false).when(chain).exists();
        doReturn(true).when(service).updateById(any(SysDictType.class));

        service.updateDictType(request(10L, "new"));

        verify(refresher).removeTypeAfterCommit("old");
        verify(refresher).refreshTypesAfterCommit("new");
    }

    @Test
    void updateDictType_notRenamed_neverTouchesRefresher() {
        doReturn(entity(10L, "same")).when(service).getById(10L);
        doReturn(true).when(service).updateById(any(SysDictType.class));

        service.updateDictType(request(10L, "same"));

        verifyNoInteractions(refresher);
    }

    @Test
    void addDictType_neverTouchesRefresher() {
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<SysDictType> chain = mock(LambdaQueryChainWrapper.class);
        doReturn(chain).when(service).lambdaQuery();
        doReturn(chain).when(chain).eq(any(SFunction.class), any());
        doReturn(false).when(chain).exists();
        doReturn(true).when(service).save(any(SysDictType.class));

        service.addDictType(request(null, "fresh"));

        // 新类型尚无数据：首个数据写入口再触发刷新
        verifyNoInteractions(refresher);
    }
}

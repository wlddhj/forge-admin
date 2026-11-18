package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.cache.DictTransCacheRefresher;
import com.forge.common.exception.BusinessException;
import com.forge.modules.system.dto.dict.DictDataBatchSaveRequest;
import com.forge.modules.system.dto.dict.DictDataQueryRequest;
import com.forge.modules.system.dto.dict.DictDataRequest;
import com.forge.modules.system.dto.dict.DictDataResponse;
import com.forge.modules.system.entity.SysDictData;
import com.forge.modules.system.mapper.SysDictDataMapper;
import com.forge.modules.system.service.SysDictDataService;
import com.forge.modules.system.util.DictLabelUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements SysDictDataService {

    private final SysDictDataMapper sysDictDataMapper;
    private final DictTransCacheRefresher dictTransCacheRefresher;

    @Override
    public Page<DictDataResponse> pageDictData(DictDataQueryRequest request) {
        Page<SysDictData> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getDictType()), SysDictData::getDictType, request.getDictType())
                .like(StrUtil.isNotBlank(request.getDictLabel()), SysDictData::getDictLabel, request.getDictLabel())
                .eq(request.getStatus() != null, SysDictData::getStatus, request.getStatus())
                .orderByAsc(SysDictData::getDictSort)
                .orderByDesc(SysDictData::getCreateTime);

        Page<SysDictData> dataPage = sysDictDataMapper.selectPage(page, wrapper);

        Page<DictDataResponse> responsePage = new Page<>();
        responsePage.setCurrent(dataPage.getCurrent());
        responsePage.setSize(dataPage.getSize());
        responsePage.setTotal(dataPage.getTotal());
        responsePage.setRecords(dataPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    @Cacheable(value = "dictData", key = "#dictType", unless = "#result == null || #result.isEmpty()")
    public List<DictDataResponse> getDictDataByType(String dictType) {
        return lambdaQuery()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, 1)
                .orderByAsc(SysDictData::getDictSort)
                .list()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DictDataResponse getDictDataDetail(Long id) {
        SysDictData dictData = getById(id);
        if (dictData == null) {
            throw new BusinessException(404, "字典数据不存在");
        }
        return convertToResponse(dictData);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictData", allEntries = true)
    public void addDictData(DictDataRequest request) {
        SysDictData dictData = new SysDictData();
        BeanUtil.copyProperties(request, dictData);
        save(dictData);
        // 字典写路径统一收尾：Redis 由 @CacheEvict 失效，本地缓存在此即时失效
        // （多实例部署下其他实例靠 DictLabelUtils 的 60s TTL 收敛）
        DictLabelUtils.evictLocalCache();
        // easy-trans DICTIONARY 翻译缓存 afterCommit 重建（pub/sub 通知其他实例）
        dictTransCacheRefresher.refreshTypesAfterCommit(request.getDictType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictData", allEntries = true)
    public void updateDictData(DictDataRequest request) {
        SysDictData dictData = getById(request.getId());
        if (dictData == null) {
            throw new BusinessException(404, "字典数据不存在");
        }
        String oldDictType = dictData.getDictType();
        BeanUtil.copyProperties(request, dictData);
        updateById(dictData);
        DictLabelUtils.evictLocalCache();
        // 码值可能被挪到别的类型：旧/新类型都刷（refresher 内部去重）
        dictTransCacheRefresher.refreshTypesAfterCommit(oldDictType, request.getDictType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictData", allEntries = true)
    public void deleteDictData(List<Long> ids) {
        // 删前收集受影响类型（删后无法回查）
        List<String> dictTypes = listByIds(ids).stream()
                .map(SysDictData::getDictType)
                .distinct()
                .toList();
        removeByIds(ids);
        DictLabelUtils.evictLocalCache();
        dictTransCacheRefresher.refreshTypesAfterCommit(dictTypes.toArray(String[]::new));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictData", allEntries = true)
    public void updateStatus(Long id, Integer status) {
        SysDictData dictData = getById(id);
        if (dictData == null) {
            throw new BusinessException(404, "字典数据不存在");
        }
        dictData.setStatus(status);
        updateById(dictData);
        DictLabelUtils.evictLocalCache();
        // 停启影响启用项集合，翻译 Map 需重建
        dictTransCacheRefresher.refreshTypesAfterCommit(dictData.getDictType());
    }

    @Override
    // beforeInvocation=true：先进方法即清 Redis 缓存，方法体内 refreshAll 的重查才会
    // miss 回源新值——默认(返回时才 evict)会让重建读到未清的旧缓存
    @CacheEvict(value = "dictData", allEntries = true, beforeInvocation = true)
    public void refreshCache() {
        log.info("刷新字典数据缓存");
        DictLabelUtils.evictLocalCache();
        // 全量重建 easy-trans DICTIONARY 翻译缓存（含 pub/sub 通知其他实例）
        dictTransCacheRefresher.refreshAll();
    }

    private DictDataResponse convertToResponse(SysDictData dictData) {
        DictDataResponse response = new DictDataResponse();
        BeanUtil.copyProperties(dictData, response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictData", allEntries = true)
    public void batchSaveDictData(DictDataBatchSaveRequest request) {
        // 查询该 dictType 下所有现有数据的 id
        List<Long> existIds = lambdaQuery()
                .eq(SysDictData::getDictType, request.getDictType())
                .select(SysDictData::getId)
                .list()
                .stream()
                .map(SysDictData::getId)
                .toList();

        // 收集前端传来的有效 id
        Set<Long> submittedIds = new HashSet<>();
        if (request.getDataList() != null) {
            for (DictDataRequest item : request.getDataList()) {
                item.setDictType(request.getDictType());
                if (item.getId() != null) {
                    submittedIds.add(item.getId());
                    // id 存在则更新
                    SysDictData dictData = getById(item.getId());
                    if (dictData != null) {
                        BeanUtil.copyProperties(item, dictData);
                        updateById(dictData);
                    }
                } else {
                    // id 不存在则新增
                    SysDictData dictData = new SysDictData();
                    BeanUtil.copyProperties(item, dictData);
                    save(dictData);
                }
            }
        }

        // 数据库中存在但前端没有传的，说明已被删除
        List<Long> toDeleteIds = existIds.stream()
                .filter(id -> !submittedIds.contains(id))
                .collect(Collectors.toList());
        if (!toDeleteIds.isEmpty()) {
            removeByIds(toDeleteIds);
        }
        DictLabelUtils.evictLocalCache();
        // 批量保存只作用于单一 dictType（子项类型被强制对齐）
        dictTransCacheRefresher.refreshTypesAfterCommit(request.getDictType());
    }
}

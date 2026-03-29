package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.modules.system.dto.dict.DictDataQueryRequest;
import com.forge.admin.modules.system.dto.dict.DictDataRequest;
import com.forge.admin.modules.system.dto.dict.DictDataResponse;
import com.forge.admin.modules.system.entity.SysDictData;
import com.forge.admin.modules.system.mapper.SysDictDataMapper;
import com.forge.admin.modules.system.service.SysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements SysDictDataService {

    private final SysDictDataMapper sysDictDataMapper;

    @Override
    public Page<DictDataResponse> pageDictData(DictDataQueryRequest request) {
        Page<SysDictData> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(request.getDictType()), SysDictData::getDictType, request.getDictType())
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictData", allEntries = true)
    public void updateDictData(DictDataRequest request) {
        SysDictData dictData = getById(request.getId());
        if (dictData == null) {
            throw new BusinessException(404, "字典数据不存在");
        }
        BeanUtil.copyProperties(request, dictData);
        updateById(dictData);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictData", allEntries = true)
    public void deleteDictData(List<Long> ids) {
        removeByIds(ids);
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
    }

    private DictDataResponse convertToResponse(SysDictData dictData) {
        DictDataResponse response = new DictDataResponse();
        BeanUtil.copyProperties(dictData, response);
        return response;
    }
}

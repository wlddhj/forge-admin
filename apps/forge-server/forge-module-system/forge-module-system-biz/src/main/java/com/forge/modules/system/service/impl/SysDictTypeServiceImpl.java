package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.cache.DictTransCacheRefresher;
import com.forge.common.exception.BusinessException;
import com.forge.modules.system.dto.dict.DictTypeQueryRequest;
import com.forge.modules.system.dto.dict.DictTypeRequest;
import com.forge.modules.system.dto.dict.DictTypeResponse;
import com.forge.modules.system.entity.SysDictData;
import com.forge.modules.system.entity.SysDictType;
import com.forge.modules.system.mapper.SysDictDataMapper;
import com.forge.modules.system.mapper.SysDictTypeMapper;
import com.forge.modules.system.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements SysDictTypeService {

    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysDictDataMapper sysDictDataMapper;
    private final DictTransCacheRefresher dictTransCacheRefresher;

    @Override
    public Page<DictTypeResponse> pageDictTypes(DictTypeQueryRequest request) {
        Page<SysDictType> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getDictName()), SysDictType::getDictName, request.getDictName())
                .like(StrUtil.isNotBlank(request.getDictType()), SysDictType::getDictType, request.getDictType())
                .eq(request.getStatus() != null, SysDictType::getStatus, request.getStatus())
                .orderByDesc(SysDictType::getCreateTime);

        Page<SysDictType> typePage = sysDictTypeMapper.selectPage(page, wrapper);

        Page<DictTypeResponse> responsePage = new Page<>();
        responsePage.setCurrent(typePage.getCurrent());
        responsePage.setSize(typePage.getSize());
        responsePage.setTotal(typePage.getTotal());
        responsePage.setRecords(typePage.getRecords().stream()
                .map(this::convertToResponse)
                .peek(response -> {
                    LambdaQueryWrapper<SysDictData> countWrapper = new LambdaQueryWrapper<>();
                    countWrapper.eq(SysDictData::getDictType, response.getDictType());
                    response.setDataCount(sysDictDataMapper.selectCount(countWrapper));
                })
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public List<DictTypeResponse> getAllDictTypes() {
        return lambdaQuery()
                .eq(SysDictType::getStatus, 1)
                .orderByAsc(SysDictType::getDictName)
                .list()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DictTypeResponse getDictTypeDetail(Long id) {
        SysDictType dictType = getById(id);
        if (dictType == null) {
            throw new BusinessException(404, "字典类型不存在");
        }
        return convertToResponse(dictType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDictType(DictTypeRequest request) {
        if (lambdaQuery().eq(SysDictType::getDictType, request.getDictType()).exists()) {
            throw new BusinessException(400, "字典类型已存在");
        }

        SysDictType dictType = new SysDictType();
        BeanUtil.copyProperties(request, dictType);
        save(dictType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDictType(DictTypeRequest request) {
        SysDictType dictType = getById(request.getId());
        if (dictType == null) {
            throw new BusinessException(404, "字典类型不存在");
        }

        String oldDictType = dictType.getDictType();
        if (!oldDictType.equals(request.getDictType())) {
            if (lambdaQuery().eq(SysDictType::getDictType, request.getDictType()).exists()) {
                throw new BusinessException(400, "字典类型已存在");
            }
        }

        BeanUtil.copyProperties(request, dictType);
        updateById(dictType);
        // 类型编码变更：旧编码翻译组作废移除，新编码组重建（类型改名/状态等不关翻译，不刷）
        if (!oldDictType.equals(request.getDictType())) {
            dictTransCacheRefresher.removeTypeAfterCommit(oldDictType);
            dictTransCacheRefresher.refreshTypesAfterCommit(request.getDictType());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictTypes(List<Long> ids) {
        // 删前收集受影响类型编码（删后无法回查）
        List<String> dictTypes = listByIds(ids).stream()
                .map(SysDictType::getDictType)
                .distinct()
                .toList();
        removeByIds(ids);
        dictTypes.forEach(dictTransCacheRefresher::removeTypeAfterCommit);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysDictType dictType = getById(id);
        if (dictType == null) {
            throw new BusinessException(404, "字典类型不存在");
        }
        dictType.setStatus(status);
        updateById(dictType);
    }

    private DictTypeResponse convertToResponse(SysDictType dictType) {
        DictTypeResponse response = new DictTypeResponse();
        BeanUtil.copyProperties(dictType, response);
        return response;
    }
}

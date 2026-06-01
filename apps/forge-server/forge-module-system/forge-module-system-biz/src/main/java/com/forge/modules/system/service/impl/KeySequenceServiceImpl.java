package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.modules.system.dto.keysequence.KeySequenceQueryRequest;
import com.forge.modules.system.dto.keysequence.KeySequenceRequest;
import com.forge.modules.system.dto.keysequence.KeySequenceResponse;
import com.forge.modules.system.entity.KeySequence;
import com.forge.modules.system.mapper.KeySequenceMapper;
import com.forge.modules.system.service.KeySequenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

@Slf4j
@Service
public class KeySequenceServiceImpl extends ServiceImpl<KeySequenceMapper, KeySequence> implements KeySequenceService {

    @Override
    public Page<KeySequenceResponse> pageKeySequences(KeySequenceQueryRequest request) {
        Page<KeySequence> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<KeySequence> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(request.getKeyCategory())) {
            wrapper.like(KeySequence::getKeyCategory, request.getKeyCategory());
        }
        wrapper.orderByDesc(KeySequence::getCreateTime);
        Page<KeySequence> result = this.page(page, wrapper);
        Page<KeySequenceResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::convertToResponse).toList());
        return responsePage;
    }

    @Override
    public KeySequenceResponse getKeySequenceDetail(Long id) {
        KeySequence entity = this.getById(id);
        return convertToResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addKeySequence(KeySequenceRequest request) {
        KeySequence entity = BeanUtil.copyProperties(request, KeySequence.class);
        entity.setMaxValue(0L);
        this.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKeySequence(KeySequenceRequest request) {
        KeySequence entity = BeanUtil.copyProperties(request, KeySequence.class);
        this.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKeySequences(Long[] ids) {
        this.removeBatchByIds(Arrays.asList(ids));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String getNextKey(String category) {
        return generateKey(category, "");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String getNextKey(String category, String arg0) {
        return generateKey(category, arg0);
    }

    private String generateKey(String category, String arg0) {
        if (StrUtil.isBlank(category)) {
            return "";
        }
        KeySequence entity = nextKey(category);
        if (Objects.isNull(entity)) {
            return null;
        }
        String prefix = entity.getKeyPrefix();
        if (StrUtil.isNotBlank(prefix) && prefix.contains("{0}")) {
            prefix = MessageFormat.format(prefix, arg0);
        }
        prefix = StrUtil.isBlank(prefix) ? "" : prefix;
        String fmt = String.format("%0" + entity.getSeqLength() + "d", entity.getMaxValue());
        return prefix + StrUtil.trimToEmpty(entity.getLastDateVal()) + fmt;
    }

    private synchronized KeySequence nextKey(String category) {
        KeySequence entity = baseMapper.findByKeyCategory(category);
        if (Objects.isNull(entity)) {
            log.warn("[KeySequence] 未找到序列配置: category={}", category);
            return null;
        }
        long maxVal = 1L;
        String dateVal = null;
        if (StrUtil.isNotBlank(entity.getDateRule())) {
            dateVal = DateUtil.format(new Date(), entity.getDateRule());
            if (StrUtil.equals(dateVal, entity.getLastDateVal())) {
                maxVal = entity.getMaxValue() + 1;
            }
        } else {
            maxVal = entity.getMaxValue() + 1;
        }
        entity.setMaxValue(maxVal);
        entity.setLastDateVal(dateVal);
        baseMapper.updateById(entity);
        return entity;
    }

    private KeySequenceResponse convertToResponse(KeySequence entity) {
        return BeanUtil.copyProperties(entity, KeySequenceResponse.class);
    }
}

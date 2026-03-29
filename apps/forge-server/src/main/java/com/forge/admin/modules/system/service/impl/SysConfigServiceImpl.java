package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.modules.system.dto.config.ConfigQueryRequest;
import com.forge.admin.modules.system.dto.config.ConfigRequest;
import com.forge.admin.modules.system.dto.config.ConfigResponse;
import com.forge.admin.modules.system.entity.SysConfig;
import com.forge.admin.modules.system.mapper.SysConfigMapper;
import com.forge.admin.modules.system.service.SysConfigService;
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
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    @Override
    public Page<ConfigResponse> pageConfigs(ConfigQueryRequest request) {
        Page<SysConfig> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getConfigName()), SysConfig::getConfigName, request.getConfigName())
                .like(StrUtil.isNotBlank(request.getConfigKey()), SysConfig::getConfigKey, request.getConfigKey())
                .eq(request.getStatus() != null, SysConfig::getStatus, request.getStatus())
                .orderByDesc(SysConfig::getCreateTime);

        Page<SysConfig> configPage = sysConfigMapper.selectPage(page, wrapper);

        Page<ConfigResponse> responsePage = new Page<>();
        responsePage.setCurrent(configPage.getCurrent());
        responsePage.setSize(configPage.getSize());
        responsePage.setTotal(configPage.getTotal());
        responsePage.setRecords(configPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public ConfigResponse getConfigDetail(Long id) {
        SysConfig config = getById(id);
        if (config == null) {
            throw new BusinessException(404, "配置不存在");
        }
        return convertToResponse(config);
    }

    @Override
    @Cacheable(value = "sysConfig", key = "#configKey", unless = "#result == null")
    public String getConfigValueByKey(String configKey) {
        SysConfig config = lambdaQuery()
                .eq(SysConfig::getConfigKey, configKey)
                .eq(SysConfig::getStatus, 1)
                .one();
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "sysConfig", allEntries = true)
    public void addConfig(ConfigRequest request) {
        if (lambdaQuery().eq(SysConfig::getConfigKey, request.getConfigKey()).exists()) {
            throw new BusinessException(400, "配置键已存在");
        }

        SysConfig config = new SysConfig();
        BeanUtil.copyProperties(request, config);
        save(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "sysConfig", key = "#request.configKey"),
            @CacheEvict(value = "sysConfig", allEntries = true)
    })
    public void updateConfig(ConfigRequest request) {
        SysConfig config = getById(request.getId());
        if (config == null) {
            throw new BusinessException(404, "配置不存在");
        }

        if (!config.getConfigKey().equals(request.getConfigKey())) {
            if (lambdaQuery().eq(SysConfig::getConfigKey, request.getConfigKey()).exists()) {
                throw new BusinessException(400, "配置键已存在");
            }
        }

        BeanUtil.copyProperties(request, config);
        updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "sysConfig", allEntries = true)
    public void deleteConfigs(List<Long> ids) {
        removeByIds(ids);
    }

    private ConfigResponse convertToResponse(SysConfig config) {
        ConfigResponse response = new ConfigResponse();
        BeanUtil.copyProperties(config, response);
        return response;
    }
}

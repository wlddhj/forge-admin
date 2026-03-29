package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.modules.system.dto.file.FileConfigQueryRequest;
import com.forge.admin.modules.system.dto.file.FileConfigRequest;
import com.forge.admin.modules.system.dto.file.FileConfigResponse;
import com.forge.admin.modules.system.entity.SysFileConfig;
import com.forge.admin.modules.system.mapper.SysFileConfigMapper;
import com.forge.admin.modules.system.service.SysFileConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件存储配置服务实现
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileConfigServiceImpl extends ServiceImpl<SysFileConfigMapper, SysFileConfig> implements SysFileConfigService {

    private static final String CACHE_NAME = "fileConfig";

    @Override
    public Page<FileConfigResponse> pageFileConfig(FileConfigQueryRequest request) {
        Page<SysFileConfig> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysFileConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getConfigName()), SysFileConfig::getConfigName, request.getConfigName())
                .eq(StrUtil.isNotBlank(request.getStorageType()), SysFileConfig::getStorageType, request.getStorageType())
                .eq(request.getStatus() != null, SysFileConfig::getStatus, request.getStatus())
                .orderByDesc(SysFileConfig::getCreateTime);
        Page<SysFileConfig> configPage = baseMapper.selectPage(page, wrapper);
        Page<FileConfigResponse> responsePage = new Page<>();
        responsePage.setCurrent(configPage.getCurrent());
        responsePage.setSize(configPage.getSize());
        responsePage.setTotal(configPage.getTotal());
        responsePage.setRecords(configPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public FileConfigResponse getFileConfigDetail(Long id) {
        SysFileConfig config = getById(id);
        if (config == null) {
            throw new BusinessException(404, "配置不存在");
        }
        return convertToResponse(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void addFileConfig(FileConfigRequest request) {
        SysFileConfig config = new SysFileConfig();
        BeanUtil.copyProperties(request, config);
        // 如果设置为默认，先取消其他默认配置
        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            clearDefaultFlag();
        }
        save(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void updateFileConfig(FileConfigRequest request) {
        SysFileConfig config = getById(request.getId());
        if (config == null) {
            throw new BusinessException(404, "配置不存在");
        }
        BeanUtil.copyProperties(request, config);
        // 如果设置为默认，先取消其他默认配置
        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            clearDefaultFlag();
        }
        updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteFileConfig(List<Long> ids) {
        removeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void setDefaultConfig(Long id) {
        SysFileConfig config = getById(id);
        if (config == null) {
            throw new BusinessException(404, "配置不存在");
        }
        // 先清除所有默认标记
        clearDefaultFlag();
        // 设置当前配置为默认
        config.setIsDefault(1);
        updateById(config);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'default'")
    public SysFileConfig getDefaultConfig() {
        List<SysFileConfig> list = lambdaQuery()
                .eq(SysFileConfig::getIsDefault, 1)
                .eq(SysFileConfig::getStatus, 1)
                .list();
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void updateStatus(Long id, Integer status) {
        SysFileConfig config = getById(id);
        if (config == null) {
            throw new BusinessException(404, "配置不存在");
        }
        config.setStatus(status);
        updateById(config);
    }

    /**
     * 清除所有默认标记
     */
    private void clearDefaultFlag() {
        lambdaUpdate()
                .set(SysFileConfig::getIsDefault, 0)
                .eq(SysFileConfig::getIsDefault, 1)
                .update();
    }

    @Override
    public void testConnection(Long id) {
        SysFileConfig config = getById(id);
        if (config == null) {
            throw new BusinessException(404, "配置不存在");
        }
        // TODO: 实现实际的连接测试逻辑
        // 目前只验证配置存在且已启用
        if (config.getStatus() != 1) {
            throw new BusinessException(400, "配置已禁用，无法测试");
        }
        log.info("[文件配置] 测试连接成功: {}", config.getConfigName());
    }

    private FileConfigResponse convertToResponse(SysFileConfig config) {
        FileConfigResponse response = new FileConfigResponse();
        BeanUtil.copyProperties(config, response);
        // AccessKey 脱敏处理
        if (StrUtil.isNotBlank(config.getAccessKey())) {
            String accessKey = config.getAccessKey();
            if (accessKey.length() > 4) {
                response.setAccessKey(accessKey.substring(0, 4) + "****");
            }
        }
        return response;
    }
}

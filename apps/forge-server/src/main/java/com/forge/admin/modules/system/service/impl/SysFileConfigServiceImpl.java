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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        if (config.getStatus() != 1) {
            throw new BusinessException(400, "配置已禁用，无法测试");
        }

        String storageType = config.getStorageType();
        try {
            switch (storageType) {
                case "local" -> testLocalConnection(config);
                case "aliyun_oss" -> testAliyunOssConnection(config);
                case "tencent_cos" -> testTencentCosConnection(config);
                case "minio" -> testMinioConnection(config);
                default -> throw new BusinessException(400, "不支持的存储类型: " + storageType);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[文件配置] 连接测试失败: {}", config.getConfigName(), e);
            throw new BusinessException(500, "连接测试失败: " + e.getMessage());
        }
        log.info("[文件配置] 连接测试成功: {}", config.getConfigName());
    }

    private void testLocalConnection(SysFileConfig config) {
        String basePath = StrUtil.blankToDefault(config.getBasePath(), "/uploads");
        Path path = Paths.get(basePath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                throw new BusinessException(500, "无法创建目录: " + basePath + " - " + e.getMessage());
            }
        }
        File dir = path.toFile();
        if (!dir.canWrite()) {
            throw new BusinessException(500, "目录不可写: " + basePath);
        }
    }

    private void testAliyunOssConnection(SysFileConfig config) {
        com.aliyun.oss.OSS ossClient = new com.aliyun.oss.OSSClientBuilder()
                .build(config.getEndpoint(), config.getAccessKey(), config.getSecretKey());
        try {
            boolean exists = ossClient.doesBucketExist(config.getBucketName());
            if (!exists) {
                throw new BusinessException(500, "存储桶不存在: " + config.getBucketName());
            }
        } finally {
            ossClient.shutdown();
        }
    }

    private void testTencentCosConnection(SysFileConfig config) {
        com.qcloud.cos.COSClient cosClient = createCosClient(config);
        try {
            cosClient.doesBucketExist(config.getBucketName());
        } finally {
            cosClient.shutdown();
        }
    }

    private com.qcloud.cos.COSClient createCosClient(SysFileConfig config) {
        com.qcloud.cos.auth.BasicCOSCredentials credentials =
                new com.qcloud.cos.auth.BasicCOSCredentials(config.getAccessKey(), config.getSecretKey());
        com.qcloud.cos.region.Region region = new com.qcloud.cos.region.Region(
                extractRegionFromEndpoint(config.getEndpoint()));
        com.qcloud.cos.ClientConfig clientConfig = new com.qcloud.cos.ClientConfig(region);
        return new com.qcloud.cos.COSClient(credentials, clientConfig);
    }

    private void testMinioConnection(SysFileConfig config) {
        io.minio.MinioClient minioClient = io.minio.MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder()
                            .bucket(config.getBucketName())
                            .build());
            if (!exists) {
                throw new BusinessException(500, "存储桶不存在: " + config.getBucketName());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractRegionFromEndpoint(String endpoint) {
        // 从 endpoint 中提取 region，例如 cos.ap-shanghai.myqcloud.com -> ap-shanghai
        if (StrUtil.isBlank(endpoint)) {
            return "ap-guangzhou";
        }
        String[] parts = endpoint.replace("https://", "").replace("http://", "").split("\\.");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "ap-guangzhou";
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

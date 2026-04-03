package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.modules.system.dto.attachment.AttachmentQueryRequest;
import com.forge.admin.modules.system.dto.attachment.AttachmentResponse;
import com.forge.admin.modules.system.entity.SysAttachment;
import com.forge.admin.modules.system.entity.SysFileConfig;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysAttachmentMapper;
import com.forge.admin.modules.system.service.SysAttachmentService;
import com.forge.admin.modules.system.service.SysFileConfigService;
import com.forge.admin.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 附件服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAttachmentServiceImpl extends ServiceImpl<SysAttachmentMapper, SysAttachment> implements SysAttachmentService {

    private final SysAttachmentMapper sysAttachmentMapper;
    private final SysUserService sysUserService;
    private final SysFileConfigService sysFileConfigService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public Page<AttachmentResponse> pageAttachments(AttachmentQueryRequest request) {
        Page<SysAttachment> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysAttachment> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getFileName())) {
            wrapper.like(SysAttachment::getFileName, request.getFileName())
                    .or()
                    .like(SysAttachment::getOriginalName, request.getFileName());
        }
        if (StringUtils.hasText(request.getFileType())) {
            wrapper.like(SysAttachment::getFileType, request.getFileType());
        }
        if (StringUtils.hasText(request.getStorageType())) {
            wrapper.eq(SysAttachment::getStorageType, request.getStorageType());
        }
        if (StringUtils.hasText(request.getUploaderName())) {
            wrapper.like(SysAttachment::getUploaderName, request.getUploaderName());
        }
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(SysAttachment::getCreateTime, LocalDateTime.parse(request.getStartTime(), DATE_TIME_FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(SysAttachment::getCreateTime, LocalDateTime.parse(request.getEndTime(), DATE_TIME_FORMATTER));
        }
        wrapper.orderByDesc(SysAttachment::getCreateTime);

        Page<SysAttachment> result = sysAttachmentMapper.selectPage(page, wrapper);

        Page<AttachmentResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::convertToResponse).toList());
        return responsePage;
    }

    @Override
    public AttachmentResponse getAttachmentDetail(Long id) {
        SysAttachment attachment = sysAttachmentMapper.selectById(id);
        if (attachment == null) {
            throw new RuntimeException("附件不存在");
        }
        return convertToResponse(attachment);
    }

    @Override
    public AttachmentResponse upload(MultipartFile file, String bizType, Long bizId) {
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        SysFileConfig config = getRequiredDefaultConfig();

        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        String datePath = LocalDateTime.now().format(DATE_PATH_FORMATTER);
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String key = datePath + "/" + newFileName;

        // 根据存储类型上传
        String filePath;
        String fileUrl;
        String storageType = config.getStorageType();

        try {
            switch (storageType) {
                case "local" -> {
                    String[] result = uploadToLocal(config, file, key, newFileName);
                    filePath = result[0];
                    fileUrl = result[1];
                }
                case "aliyun_oss" -> {
                    filePath = config.getBucketName() + "/" + key;
                    fileUrl = uploadToAliyunOss(config, file, key);
                }
                case "tencent_cos" -> {
                    filePath = config.getBucketName() + "/" + key;
                    fileUrl = uploadToTencentCos(config, file, key);
                }
                case "minio" -> {
                    filePath = config.getBucketName() + "/" + key;
                    fileUrl = uploadToMinio(config, file, key);
                }
                default -> throw new RuntimeException("不支持的存储类型: " + storageType);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }

        // 获取当前用户
        Long uploaderId = null;
        String uploaderName = "system";
        try {
            SysUser currentUser = sysUserService.getCurrentUser();
            if (currentUser != null) {
                uploaderId = currentUser.getId();
                uploaderName = currentUser.getNickname();
            }
        } catch (Exception e) {
            // 忽略获取用户失败的错误
        }

        // 保存附件记录
        SysAttachment attachment = new SysAttachment();
        attachment.setFileName(newFileName);
        attachment.setOriginalName(originalName);
        attachment.setFilePath(filePath);
        attachment.setFileUrl(fileUrl);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());
        attachment.setFileExtension(extension);
        attachment.setStorageType(storageType);
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachment.setUploaderId(uploaderId);
        attachment.setUploaderName(uploaderName);

        sysAttachmentMapper.insert(attachment);

        return convertToResponse(attachment);
    }

    @Override
    public void deleteAttachments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        for (Long id : ids) {
            SysAttachment attachment = sysAttachmentMapper.selectById(id);
            if (attachment != null) {
                deleteFile(attachment);
            }
            sysAttachmentMapper.deleteById(id);
        }
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("只能上传图片文件");
        }

        // 校验文件大小（最大2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new RuntimeException("图片大小不能超过2MB");
        }

        SysFileConfig config = getRequiredDefaultConfig();

        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        String datePath = LocalDateTime.now().format(DATE_PATH_FORMATTER);
        String newFileName = "avatar_" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String key = "avatar/" + datePath + "/" + newFileName;

        try {
            String storageType = config.getStorageType();
            return switch (storageType) {
                case "local" -> uploadToLocal(config, file, key, newFileName)[1];
                case "aliyun_oss" -> uploadToAliyunOss(config, file, key);
                case "tencent_cos" -> uploadToTencentCos(config, file, key);
                case "minio" -> uploadToMinio(config, file, key);
                default -> throw new RuntimeException("不支持的存储类型: " + storageType);
            };
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("头像上传失败", e);
            throw new RuntimeException("头像上传失败: " + e.getMessage());
        }
    }

    // ==================== 本地存储 ====================

    private String[] uploadToLocal(SysFileConfig config, MultipartFile file, String key, String newFileName) throws IOException {
        String basePath = StrUtil.blankToDefault(config.getBasePath(), "./uploads");
        Path absoluteBasePath = Paths.get(basePath).toAbsolutePath().normalize();

        // 创建日期目录
        Path dirPath = absoluteBasePath.resolve(Paths.get(key).getParent());
        Files.createDirectories(dirPath);

        // 保存文件
        Path filePath = dirPath.resolve(newFileName);
        file.transferTo(filePath.toFile());

        // 构建 URL
        String domain = StrUtil.blankToDefault(config.getDomain(),
                "http://localhost:8181/api" + absoluteBasePath.getFileName());
        String fileUrl = domain + "/" + key;

        return new String[]{filePath.toString(), fileUrl};
    }

    private void deleteLocalFile(SysAttachment attachment) {
        try {
            Path filePath = Paths.get(attachment.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除本地文件失败: {}", attachment.getFilePath(), e);
        }
    }

    // ==================== 阿里云 OSS ====================

    private String uploadToAliyunOss(SysFileConfig config, MultipartFile file, String key) throws Exception {
        com.aliyun.oss.OSS ossClient = new com.aliyun.oss.OSSClientBuilder()
                .build(config.getEndpoint(), config.getAccessKey(), config.getSecretKey());
        try {
            ossClient.putObject(config.getBucketName(), key, new ByteArrayInputStream(file.getBytes()));
            // 构建访问 URL
            String domain = config.getDomain();
            if (StrUtil.isNotBlank(domain)) {
                return domain + "/" + key;
            }
            return "https://" + config.getBucketName() + "." + config.getEndpoint() + "/" + key;
        } finally {
            ossClient.shutdown();
        }
    }

    private void deleteFromAliyunOss(SysFileConfig config, String key) {
        com.aliyun.oss.OSS ossClient = new com.aliyun.oss.OSSClientBuilder()
                .build(config.getEndpoint(), config.getAccessKey(), config.getSecretKey());
        try {
            ossClient.deleteObject(config.getBucketName(), key);
        } finally {
            ossClient.shutdown();
        }
    }

    // ==================== 腾讯云 COS ====================

    private String uploadToTencentCos(SysFileConfig config, MultipartFile file, String key) throws Exception {
        com.qcloud.cos.COSClient cosClient = createCosClient(config);
        try {
            com.qcloud.cos.model.ObjectMetadata metadata = new com.qcloud.cos.model.ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (StrUtil.isNotBlank(file.getContentType())) {
                metadata.setContentType(file.getContentType());
            }
            cosClient.putObject(config.getBucketName(), key, new ByteArrayInputStream(file.getBytes()), metadata);
            String domain = config.getDomain();
            if (StrUtil.isNotBlank(domain)) {
                return domain + "/" + key;
            }
            return "https://" + config.getBucketName() + "." + config.getEndpoint() + "/" + key;
        } finally {
            cosClient.shutdown();
        }
    }

    private void deleteFromTencentCos(SysFileConfig config, String key) {
        com.qcloud.cos.COSClient cosClient = createCosClient(config);
        try {
            cosClient.deleteObject(config.getBucketName(), key);
        } finally {
            cosClient.shutdown();
        }
    }

    private com.qcloud.cos.COSClient createCosClient(SysFileConfig config) {
        com.qcloud.cos.auth.BasicCOSCredentials credentials =
                    new com.qcloud.cos.auth.BasicCOSCredentials(config.getAccessKey(), config.getSecretKey());
            String region = extractRegionFromEndpoint(config.getEndpoint());
            com.qcloud.cos.ClientConfig clientConfig = new com.qcloud.cos.ClientConfig(new com.qcloud.cos.region.Region(region));
            return new com.qcloud.cos.COSClient(credentials, clientConfig);
    }

    // ==================== MinIO ====================

    private String uploadToMinio(SysFileConfig config, MultipartFile file, String key) throws Exception {
        io.minio.MinioClient minioClient = io.minio.MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(file.getBytes())) {
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(config.getBucketName())
                            .object(key)
                            .stream(bis, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
        }

        String domain = config.getDomain();
        if (StrUtil.isNotBlank(domain)) {
            return domain + "/" + key;
        }
        return config.getEndpoint() + "/" + config.getBucketName() + "/" + key;
    }

    private void deleteFromMinio(SysFileConfig config, String key) {
        try {
            io.minio.MinioClient minioClient = io.minio.MinioClient.builder()
                    .endpoint(config.getEndpoint())
                    .credentials(config.getAccessKey(), config.getSecretKey())
                    .build();
            minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                            .bucket(config.getBucketName())
                            .object(key)
                            .build());
        } catch (Exception e) {
            log.warn("MinIO 删除文件失败: {}", key, e);
        }
    }

    // ==================== 通用方法 ====================

    private SysFileConfig getRequiredDefaultConfig() {
        SysFileConfig config = sysFileConfigService.getDefaultConfig();
        if (config == null) {
            throw new RuntimeException("请先在文件配置中设置默认存储配置");
        }
        return config;
    }

    private void deleteFile(SysAttachment attachment) {
        String storageType = attachment.getStorageType();
        try {
            switch (storageType) {
                case "local" -> deleteLocalFile(attachment);
                case "aliyun_oss", "tencent_cos", "minio" -> deleteCloudFile(attachment);
                default -> log.warn("不支持的存储类型，跳过删除: {}", storageType);
            }
        } catch (Exception e) {
            log.warn("删除文件失败: id={}, storageType={}", attachment.getId(), storageType, e);
        }
    }

    private void deleteCloudFile(SysAttachment attachment) {
        // 云端存储需要获取当前配置来获取凭证
        // filePath 格式: bucketName/datePath/fileName，从中提取 key
        String filePath = attachment.getFilePath();
        String key;
        if (filePath.contains("/")) {
            // 去掉 bucketName 前缀
            key = filePath.substring(filePath.indexOf("/") + 1);
        } else {
            key = filePath;
        }

        // 使用 fileUrl 中的 host 信息匹配配置
        // 更可靠的方式：获取默认配置（假设存储配置不变）
        SysFileConfig config = sysFileConfigService.getDefaultConfig();
        if (config == null) {
            log.warn("无法获取文件存储配置，跳过云端删除: {}", attachment.getId());
            return;
        }

        switch (attachment.getStorageType()) {
            case "aliyun_oss" -> deleteFromAliyunOss(config, key);
            case "tencent_cos" -> deleteFromTencentCos(config, key);
            case "minio" -> deleteFromMinio(config, key);
        }
    }

    private String extractRegionFromEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint)) {
            return "ap-guangzhou";
        }
        String[] parts = endpoint.replace("https://", "").replace("http://", "").split("\\.");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "ap-guangzhou";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private AttachmentResponse convertToResponse(SysAttachment attachment) {
        AttachmentResponse response = new AttachmentResponse();
        BeanUtils.copyProperties(attachment, response);
        return response;
    }
}

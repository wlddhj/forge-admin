package com.forge.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.dto.attachment.AttachmentQueryRequest;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.entity.SysFileConfig;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysAttachmentMapper;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.system.service.SysFileConfigService;
import com.forge.modules.system.service.SysUserService;
import com.forge.modules.system.service.spi.AttachmentRefChecker;
import com.forge.modules.system.service.storage.FileStorageClient;
import com.forge.modules.system.service.storage.StorageResult;
import com.forge.modules.system.auth.util.FileUploadValidator;
import com.forge.framework.tenant.core.aop.TenantIgnore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final FileUploadValidator fileUploadValidator;
    private final List<FileStorageClient> storageClients;
    private final List<AttachmentRefChecker> refCheckers;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 孤儿回收白名单：仅「上传后回绑」型业务；avatar/brand_logo 为永久文件、contract_template 靠模板绑定、ai_document 靠级联删，均不回收 */
    private static final List<String> CLEANABLE_BIZ_TYPES = List.of(
            AttachmentBizTypes.CONTRACT,
            AttachmentBizTypes.WF_COMMENT,
            AttachmentBizTypes.NOTICE);
    private static final int ORPHAN_TTL_DAYS = 7;

    private final Map<String, FileStorageClient> clientMap = new HashMap<>();
    private final Map<String, AttachmentRefChecker> refCheckerMap = new HashMap<>();

    @PostConstruct
    void initClientMap() {
        storageClients.forEach(client -> clientMap.put(client.storageType(), client));
        refCheckers.forEach(checker -> refCheckerMap.put(checker.bizType(), checker));
    }

    @Override
    public Page<AttachmentResponse> pageAttachments(AttachmentQueryRequest request) {
        Page<SysAttachment> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysAttachment> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getFileName())) {
            wrapper.and(w -> w.like(SysAttachment::getFileName, request.getFileName())
                    .or()
                    .like(SysAttachment::getOriginalName, request.getFileName()));
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
        if (StringUtils.hasText(request.getBizType())) {
            wrapper.eq(SysAttachment::getBizType, request.getBizType());
        }
        if (request.getBizId() != null) {
            wrapper.eq(SysAttachment::getBizId, request.getBizId());
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
            throw new BusinessException(404, "附件不存在");
        }
        return convertToResponse(attachment);
    }

    @Override
    public List<AttachmentResponse> listByBiz(String bizType, Long bizId) {
        List<SysAttachment> list = lambdaQuery()
                .eq(SysAttachment::getBizType, bizType)
                .eq(SysAttachment::getBizId, bizId)
                .orderByAsc(SysAttachment::getCreateTime)
                .list();
        return list.stream().map(this::convertToResponse).toList();
    }

    @Override
    public AttachmentResponse upload(MultipartFile file, String bizType, Long bizId) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        // 安全校验：大小、扩展名、Magic Number
        String validationError = fileUploadValidator.validate(file);
        if (validationError != null) {
            throw new BusinessException(400, validationError);
        }

        try (InputStream in = file.getInputStream()) {
            return doUpload(in, file.getSize(), file.getContentType(), file.getOriginalFilename(), bizType, bizId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "读取上传内容失败: " + e.getMessage());
        }
    }

    @Override
    public AttachmentResponse upload(InputStream in, long size, String contentType, String originalName, String bizType, Long bizId) {
        // 系统内部生成内容（如合同定稿 PDF）：自产可信，跳过上传安全校验
        return doUpload(in, size, contentType, originalName, bizType, bizId);
    }

    /**
     * 上传共核：校验已在两个入口分流完成，这里只负责存储与落库
     */
    private AttachmentResponse doUpload(InputStream in, long size, String contentType, String originalName, String bizType, Long bizId) {
        SysFileConfig config = getRequiredDefaultConfig();

        String extension = getFileExtension(originalName);
        String datePath = LocalDateTime.now().format(DATE_PATH_FORMATTER);
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String key = datePath + "/" + newFileName;

        FileStorageClient client = requireClient(config.getStorageType());
        StorageResult result;
        try {
            result = client.upload(config, in, size, contentType, key);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(500, "文件上传失败: " + e.getMessage());
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
        attachment.setFilePath(result.filePath());
        attachment.setFileUrl(result.fileUrl());
        attachment.setFileSize(size);
        attachment.setFileType(contentType);
        attachment.setFileExtension(extension);
        attachment.setStorageType(config.getStorageType());
        attachment.setFileConfigId(config.getId());
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachment.setUploaderId(uploaderId);
        attachment.setUploaderName(uploaderName);

        sysAttachmentMapper.insert(attachment);

        return convertToResponse(attachment);
    }

    @Override
    public byte[] loadContent(Long attachmentId) {
        SysAttachment attachment = sysAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(404, "附件不存在");
        }
        FileStorageClient client = requireClient(attachment.getStorageType());
        SysFileConfig config = resolveStorageConfig(attachment);
        if (config == null && !"local".equals(attachment.getStorageType())) {
            throw new BusinessException(500, "无法定位附件的存储配置，读取失败: id=" + attachmentId);
        }
        try (InputStream in = client.download(config, attachment.getFilePath())) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.error("读取附件内容失败: id={}", attachmentId, e);
            throw new BusinessException(500, "读取附件内容失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteAttachments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<SysAttachment> attachments = sysAttachmentMapper.selectBatchIds(ids);

        // 引用校验：任一附件被存活业务引用即整体拒绝（fail-fast），避免删掉业务侧仍在使用的文件
        for (SysAttachment attachment : attachments) {
            AttachmentRefChecker checker = refCheckerMap.get(attachment.getBizType());
            if (checker != null) {
                String reference = checker.checkReferenced(attachment);
                if (reference != null) {
                    throw new BusinessException(400, "附件 [" + attachment.getOriginalName() + "] 被" + reference
                            + "引用，无法删除，请先在业务侧处理");
                }
            }
        }

        for (SysAttachment attachment : attachments) {
            deleteFile(attachment);
            sysAttachmentMapper.deleteById(attachment.getId());
        }
    }

    @Override
    @TenantIgnore
    public int cleanupOrphanAttachments(int batchSize) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(ORPHAN_TTL_DAYS);
        List<SysAttachment> orphans = lambdaQuery()
                .in(SysAttachment::getBizType, CLEANABLE_BIZ_TYPES)
                .isNull(SysAttachment::getBizId)
                .lt(SysAttachment::getCreateTime, threshold)
                .last("LIMIT " + batchSize)
                .list();
        if (orphans.isEmpty()) {
            return 0;
        }
        deleteAttachments(orphans.stream().map(SysAttachment::getId).toList());
        return orphans.size();
    }

    private void deleteFile(SysAttachment attachment) {
        String storageType = attachment.getStorageType();
        try {
            FileStorageClient client = requireClient(storageType);
            SysFileConfig config = resolveStorageConfig(attachment);
            if (config == null && !"local".equals(storageType)) {
                return;
            }
            client.delete(config, attachment.getFilePath());
        } catch (Exception e) {
            log.warn("删除文件失败: id={}, storageType={}", attachment.getId(), storageType, e);
        }
    }

    /**
     * 删除时定位存储配置：优先按附件记录的 fileConfigId；
     * 历史记录（fileConfigId 为空）按 filePath 的桶名前缀回退匹配
     */
    private SysFileConfig resolveStorageConfig(SysAttachment attachment) {
        if ("local".equals(attachment.getStorageType())) {
            // 本地删除仅依赖落库的绝对路径，无需配置
            return null;
        }
        if (attachment.getFileConfigId() != null) {
            SysFileConfig config = sysFileConfigService.getById(attachment.getFileConfigId());
            if (config != null) {
                return config;
            }
        }
        String filePath = attachment.getFilePath();
        if (filePath != null && filePath.contains("/")) {
            String bucket = filePath.substring(0, filePath.indexOf("/"));
            SysFileConfig fallback = sysFileConfigService.lambdaQuery()
                    .eq(SysFileConfig::getStorageType, attachment.getStorageType())
                    .eq(SysFileConfig::getBucketName, bucket)
                    .last("LIMIT 1")
                    .one();
            if (fallback != null) {
                return fallback;
            }
        }
        log.warn("无法定位附件的存储配置，跳过物理删除: id={}, storageType={}",
                attachment.getId(), attachment.getStorageType());
        return null;
    }

    private FileStorageClient requireClient(String storageType) {
        FileStorageClient client = clientMap.get(storageType);
        if (client == null) {
            throw new BusinessException(500, "不支持的存储类型: " + storageType);
        }
        return client;
    }

    private SysFileConfig getRequiredDefaultConfig() {
        SysFileConfig config = sysFileConfigService.getDefaultConfig();
        if (config == null) {
            throw new BusinessException(500, "请先在文件配置中设置默认存储配置");
        }
        return config;
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

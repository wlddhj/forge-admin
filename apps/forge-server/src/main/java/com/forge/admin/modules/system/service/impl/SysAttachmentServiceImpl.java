package com.forge.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.modules.system.dto.attachment.AttachmentQueryRequest;
import com.forge.admin.modules.system.dto.attachment.AttachmentResponse;
import com.forge.admin.modules.system.entity.SysAttachment;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysAttachmentMapper;
import com.forge.admin.modules.system.service.SysAttachmentService;
import com.forge.admin.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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
@Service
@RequiredArgsConstructor
public class SysAttachmentServiceImpl extends ServiceImpl<SysAttachmentMapper, SysAttachment> implements SysAttachmentService {

    private final SysAttachmentMapper sysAttachmentMapper;
    private final SysUserService sysUserService;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @Value("${file.base-url:http://localhost:8080/api/uploads}")
    private String baseUrl;

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

        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        String datePath = LocalDateTime.now().format(DATE_PATH_FORMATTER);
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 创建日期目录
        Path dirPath = Paths.get(uploadPath, datePath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("创建目录失败: " + e.getMessage());
        }

        // 保存文件
        Path filePath = dirPath.resolve(newFileName);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage());
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
        attachment.setFilePath(filePath.toString());
        attachment.setFileUrl(baseUrl + "/" + datePath + "/" + newFileName);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());
        attachment.setFileExtension(extension);
        attachment.setStorageType("local");
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
            if (attachment != null && "local".equals(attachment.getStorageType())) {
                // 删除本地文件
                try {
                    Path filePath = Paths.get(attachment.getFilePath());
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    // 忽略删除文件失败的错误
                }
            }
            sysAttachmentMapper.deleteById(id);
        }
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

        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        String datePath = LocalDateTime.now().format(DATE_PATH_FORMATTER);
        String newFileName = "avatar_" + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 创建日期目录
        Path dirPath = Paths.get(uploadPath, "avatar", datePath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("创建目录失败: " + e.getMessage());
        }

        // 保存文件
        Path filePath = dirPath.resolve(newFileName);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage());
        }

        // 返回头像URL
        return baseUrl + "/avatar/" + datePath + "/" + newFileName;
    }
}

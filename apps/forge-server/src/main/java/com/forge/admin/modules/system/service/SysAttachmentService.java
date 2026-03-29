package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.attachment.AttachmentQueryRequest;
import com.forge.admin.modules.system.dto.attachment.AttachmentResponse;
import com.forge.admin.modules.system.entity.SysAttachment;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件服务接口
 */
public interface SysAttachmentService extends IService<SysAttachment> {

    /**
     * 分页查询附件
     */
    Page<AttachmentResponse> pageAttachments(AttachmentQueryRequest request);

    /**
     * 获取附件详情
     */
    AttachmentResponse getAttachmentDetail(Long id);

    /**
     * 上传附件
     */
    AttachmentResponse upload(MultipartFile file, String bizType, Long bizId);

    /**
     * 删除附件
     */
    void deleteAttachments(java.util.List<Long> ids);

    /**
     * 上传头像
     * @return 头像URL
     */
    String uploadAvatar(MultipartFile file);
}

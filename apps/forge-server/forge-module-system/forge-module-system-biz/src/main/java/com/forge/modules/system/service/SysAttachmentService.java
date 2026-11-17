package com.forge.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.forge.modules.system.dto.attachment.AttachmentQueryRequest;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.entity.SysAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

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
     * 按业务类型与业务ID查询附件列表
     */
    List<AttachmentResponse> listByBiz(String bizType, Long bizId);

    /**
     * 上传附件（HTTP 入口，含大小/扩展名/Magic Number 全量安全校验）
     */
    AttachmentResponse upload(MultipartFile file, String bizType, Long bizId);

    /**
     * 上传系统内部生成的内容（如合同定稿 PDF）。
     * 内容自产可信，跳过上传安全校验；流由本方法消费完毕，调用方无需关闭
     */
    AttachmentResponse upload(InputStream in, long size, String contentType, String originalName, String bizType, Long bizId);

    /**
     * 读取附件内容（按附件行内存储配置路由到对应存储），不存在或读取失败抛业务异常
     */
    byte[] loadContent(Long attachmentId);

    /**
     * 删除附件（被存活业务引用的附件将被拒绝并提示引用方）
     */
    void deleteAttachments(List<Long> ids);

    /**
     * 回收孤儿附件（白名单 bizType、未绑定 bizId、超 7 天），返回本批回收数。
     * 定时任务上下文无租户，内部跨租户扫描；逐批调用直至返回 0
     */
    int cleanupOrphanAttachments(int batchSize);
}

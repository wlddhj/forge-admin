package com.forge.modules.workflow.dto.bizview;

import lombok.Data;

import java.util.List;

/**
 * 业务单据视图响应（审批抽屉/流程详情通用渲染）
 *
 * @author forge-admin
 */
@Data
public class WfBizViewResponse {

    /** 是否有可用的业务单据视图（false 时前端降级为实例变量快照兜底） */
    private boolean available;

    /** 业务类型（businessKey 前缀） */
    private String bizType;

    /** 业务ID（原始字符串） */
    private String bizId;

    /** 单据标题 */
    private String title;

    /** 结构化字段列表 */
    private List<WfBizViewField> fields;

    /** 附件清单（经代理端点下载） */
    private List<AttachmentItem> attachments;

    /** 是否支持单据预览（如合同 PDF） */
    private boolean previewEnabled;

    /** 预览内容 Content-Type（前端 iframe 渲染用） */
    private String previewContentType;

    /**
     * 附件清单项
     */
    @Data
    public static class AttachmentItem {

        /** 附件ID（代理下载端点入参） */
        private Long id;

        /** 原始文件名 */
        private String name;

        /** 文件大小（字节） */
        private Long size;

        /** 扩展名 */
        private String extension;
    }
}

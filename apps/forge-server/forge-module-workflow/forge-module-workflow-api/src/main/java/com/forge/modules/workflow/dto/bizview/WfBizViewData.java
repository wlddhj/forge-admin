package com.forge.modules.workflow.dto.bizview;

import lombok.Data;

import java.util.List;

/**
 * 业务单据只读视图数据（业务模块经 {@code WfBizViewProvider} 提供，
 * 审批抽屉/流程详情以通用 descriptions 渲染）
 *
 * @author forge-admin
 */
@Data
public class WfBizViewData {

    /** 单据标题（可空，缺省时前端回退实例标题） */
    private String title;

    /** 结构化字段列表（label/value 有序展示） */
    private List<WfBizViewField> fields;

    /** 附件引用（可空；workflow 侧据此查询 sys_attachment 附件清单并代理下载） */
    private WfBizViewAttachmentRef attachmentRef;

    /** 是否支持单据预览（getBizPreview 返回字节流，如合同 PDF） */
    private boolean previewEnabled;
}

package com.forge.modules.workflow.dto.bizview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务单据附件引用（指向 sys_attachment 的 biz_type + biz_id 域，
 * 由 workflow 侧统一查询附件清单并经代理端点供实例参与人下载）
 *
 * @author forge-admin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WfBizViewAttachmentRef {

    /** 附件业务类型（取 AttachmentBizTypes 常量值） */
    private String bizType;

    /** 附件业务ID */
    private Long bizId;
}

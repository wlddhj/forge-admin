package com.forge.modules.workflow.dto.im;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * IM 渠道配置保存请求（upsert：tenantId+channel 唯一）
 * <p>
 * secret 语义：null/空串 = 保留原值（编辑场景）；新建行时 secret 必填。
 */
@Data
public class ImChannelConfigRequest {

    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    /** 渠道标识：wework / dingtalk */
    @NotBlank(message = "渠道不能为空")
    private String channel;

    /** 是否启用（0/1） */
    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    /** 企业微信 corpId（wework 渠道必填） */
    private String corpId;

    /** 钉钉 appKey（dingtalk 渠道必填） */
    private String appKey;

    /** 应用 agentId */
    @NotBlank(message = "agentId 不能为空")
    private String agentId;

    /** 应用 secret（空=保留原值） */
    private String secret;

    /** 备注 */
    private String remark;
}

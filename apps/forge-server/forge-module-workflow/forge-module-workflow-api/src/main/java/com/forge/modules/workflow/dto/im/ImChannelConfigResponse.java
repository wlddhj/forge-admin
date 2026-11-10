package com.forge.modules.workflow.dto.im;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 渠道配置视图（出参不含 secret 明文，仅 hasSecret 标记）
 */
@Data
@Builder
public class ImChannelConfigResponse {

    private Long id;

    private Long tenantId;

    /** 渠道标识：wework / dingtalk */
    private String channel;

    /** 是否启用（0/1） */
    private Integer enabled;

    private String corpId;

    private String appKey;

    private String agentId;

    /** 是否已配置 secret */
    private Boolean hasSecret;

    private String remark;

    private LocalDateTime updateTime;
}

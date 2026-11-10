package com.forge.modules.system.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 企业 IM（企业微信/钉钉）userid 绑定视图
 */
@Data
@Builder
public class CorpBindingResponse {

    /** 系统用户ID */
    private Long userId;

    /** 企业微信 userid（未绑定为 null） */
    private String weworkUserid;

    /** 钉钉 userid（未绑定为 null；dingtalk_corp 手动绑定行） */
    private String dingtalkUserid;

    /** 既有 dingtalk 扫码绑定行的 unionId 是否存在（存在则推送时自动转换为 userid） */
    private Boolean dingtalkUnionIdPresent;
}

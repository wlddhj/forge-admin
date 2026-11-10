package com.forge.modules.system.auth.dto;

import lombok.Data;

/**
 * 企业 IM（企业微信/钉钉）userid 绑定请求 —— 全量态保存：
 * 非空字段 upsert 绑定，空字段（且原已绑定）解绑。
 */
@Data
public class CorpBindingRequest {

    /** 企业微信 userid（空=解绑） */
    private String weworkUserid;

    /** 钉钉 userid（空=解绑） */
    private String dingtalkUserid;
}

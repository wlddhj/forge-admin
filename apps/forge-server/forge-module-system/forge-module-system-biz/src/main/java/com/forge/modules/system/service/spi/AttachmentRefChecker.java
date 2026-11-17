package com.forge.modules.system.service.spi;

import com.forge.modules.system.entity.SysAttachment;

/**
 * 附件引用校验 SPI：业务模块按 bizType 注册，删除附件前判定是否仍被存活业务引用。
 * 实现类声明为 Spring Bean 即自动被 SysAttachmentServiceImpl 收集生效
 */
public interface AttachmentRefChecker {

    /**
     * 守卫的业务类型（取 AttachmentBizTypes 常量值）
     */
    String bizType();

    /**
     * 校验单条附件是否被存活业务引用
     *
     * @return 引用描述（如 "公告『xxx』"）；未被引用返回 null
     */
    String checkReferenced(SysAttachment attachment);
}

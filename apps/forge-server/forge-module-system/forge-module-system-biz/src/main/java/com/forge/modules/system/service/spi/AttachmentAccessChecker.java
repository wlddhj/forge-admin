package com.forge.modules.system.service.spi;

import com.forge.modules.system.entity.SysAttachment;

/**
 * 附件读权限校验 SPI：业务模块按 bizType 注册，下载附件前判定当前用户是否可读。
 * 实现类声明为 Spring Bean 即自动被 SysAttachmentServiceImpl 收集生效；
 * 未注册 checker 的 bizType 默认放行（登录即可），需要严控的类型逐个接入
 */
public interface AttachmentAccessChecker {

    /**
     * 守卫的业务类型（取 AttachmentBizTypes 常量值）
     */
    String bizType();

    /**
     * 校验用户能否读取该附件
     *
     * @param attachment 附件行（含 bizType/bizId/fileConfigId）
     * @param userId     当前登录用户
     * @return 拒绝原因描述；可读返回 null
     */
    String checkReadDenied(SysAttachment attachment, Long userId);
}

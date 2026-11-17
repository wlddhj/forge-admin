package com.forge.modules.contract.service.checker;

import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.mapper.CtTemplateMapper;
import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.spi.AttachmentRefChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 合同模板图片引用校验：附件行 bizId 指向的模板仍存活即视为被引用。
 * 绑定关系由模板保存时的图片同步维护（ContractTemplateServiceImpl#syncTemplateImageBindings）
 */
@Component
@RequiredArgsConstructor
public class ContractTemplateAttachmentRefChecker implements AttachmentRefChecker {

    private final CtTemplateMapper ctTemplateMapper;

    @Override
    public String bizType() {
        return AttachmentBizTypes.CONTRACT_TEMPLATE;
    }

    @Override
    public String checkReferenced(SysAttachment attachment) {
        if (attachment.getBizId() == null) {
            return null;
        }
        CtTemplate template = ctTemplateMapper.selectById(attachment.getBizId());
        return template != null ? "合同模板『" + template.getName() + "』" : null;
    }
}

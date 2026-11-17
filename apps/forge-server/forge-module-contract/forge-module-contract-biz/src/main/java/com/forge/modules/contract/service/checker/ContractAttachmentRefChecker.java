package com.forge.modules.contract.service.checker;

import com.forge.modules.contract.entity.CtContract;
import com.forge.modules.contract.mapper.CtContractMapper;
import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.spi.AttachmentRefChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 合同定稿附件引用校验：附件行 bizId 指向的合同仍存活即视为被引用
 */
@Component
@RequiredArgsConstructor
public class ContractAttachmentRefChecker implements AttachmentRefChecker {

    private final CtContractMapper ctContractMapper;

    @Override
    public String bizType() {
        return AttachmentBizTypes.CONTRACT;
    }

    @Override
    public String checkReferenced(SysAttachment attachment) {
        if (attachment.getBizId() == null) {
            return null;
        }
        CtContract contract = ctContractMapper.selectById(attachment.getBizId());
        return contract != null ? "合同『" + contract.getContractNo() + "』" : null;
    }
}

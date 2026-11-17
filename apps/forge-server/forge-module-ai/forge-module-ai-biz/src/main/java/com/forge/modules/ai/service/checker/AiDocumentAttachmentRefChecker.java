package com.forge.modules.ai.service.checker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.entity.AiDocument;
import com.forge.modules.ai.mapper.AiDocumentMapper;
import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.spi.AttachmentRefChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 文档附件引用校验：ai_document 经 attachment_id 关联（bizId 不落值），按附件 id 反查存活文档
 */
@Component
@RequiredArgsConstructor
public class AiDocumentAttachmentRefChecker implements AttachmentRefChecker {

    private final AiDocumentMapper aiDocumentMapper;

    @Override
    public String bizType() {
        return AttachmentBizTypes.AI_DOCUMENT;
    }

    @Override
    public String checkReferenced(SysAttachment attachment) {
        Long count = aiDocumentMapper.selectCount(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getAttachmentId, attachment.getId()));
        return count != null && count > 0 ? "AI 文档" : null;
    }
}

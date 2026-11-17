package com.forge.modules.system.service.checker;

import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.entity.SysNotice;
import com.forge.modules.system.mapper.SysNoticeMapper;
import com.forge.modules.system.service.spi.AttachmentRefChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 公告附件引用校验：附件行 bizId 指向的公告仍存活即视为被引用
 */
@Component
@RequiredArgsConstructor
public class NoticeAttachmentRefChecker implements AttachmentRefChecker {

    private final SysNoticeMapper sysNoticeMapper;

    @Override
    public String bizType() {
        return AttachmentBizTypes.NOTICE;
    }

    @Override
    public String checkReferenced(SysAttachment attachment) {
        if (attachment.getBizId() == null) {
            return null;
        }
        SysNotice notice = sysNoticeMapper.selectById(attachment.getBizId());
        return notice != null ? "公告『" + notice.getNoticeTitle() + "』" : null;
    }
}

package com.forge.modules.system.quartz.task;

import com.forge.modules.system.service.SysAttachmentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收任务循环语义：逐批调用直至服务返回 0
 */
class AttachmentCleanupTaskTest {

    @Test
    void loopsUntilBatchEmpty() {
        SysAttachmentService service = mock(SysAttachmentService.class);
        when(service.cleanupOrphanAttachments(500)).thenReturn(500, 300, 0);

        new AttachmentCleanupTask(service).execute(null);

        verify(service, times(3)).cleanupOrphanAttachments(500);
    }

    @Test
    void nothingToDoCallsOnce() {
        SysAttachmentService service = mock(SysAttachmentService.class);
        when(service.cleanupOrphanAttachments(500)).thenReturn(0);

        new AttachmentCleanupTask(service).execute(null);

        verify(service, times(1)).cleanupOrphanAttachments(500);
    }

    @Test
    void jobNameRegistered() {
        assertThat(new AttachmentCleanupTask(mock(SysAttachmentService.class)).getJobName())
                .isEqualTo("附件孤儿回收");
    }
}

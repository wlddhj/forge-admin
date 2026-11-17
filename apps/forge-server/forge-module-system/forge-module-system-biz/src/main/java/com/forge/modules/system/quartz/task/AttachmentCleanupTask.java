package com.forge.modules.system.quartz.task;

import com.forge.modules.system.job.JobHandler;
import com.forge.modules.system.service.SysAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 附件孤儿回收任务：回收「上传后回绑」型业务（合同/审批评论/公告）中
 * 上传后始终未绑定 bizId 且超过 7 天的附件（行+物理文件）。
 * 范围为最小白名单（见 SysAttachmentServiceImpl#CLEANABLE_BIZ_TYPES）：
 * avatar/brand_logo 为永久文件不回收，contract_template 由模板保存绑定维护，ai_document 靠级联删除。
 * sys_job 种子默认暂停（观察期后手动启用），invoke_target=attachmentCleanupTask.execute
 */
@Slf4j
@Component("attachmentCleanupTask")
@RequiredArgsConstructor
public class AttachmentCleanupTask implements JobHandler {

    private static final int BATCH_SIZE = 500;

    private final SysAttachmentService sysAttachmentService;

    @Override
    public String getJobName() {
        return "附件孤儿回收";
    }

    @Override
    public void execute(Map<String, Object> params) {
        int total = 0;
        int batch;
        // 逐批回收直至无孤儿；批内被引用校验拦截时抛异常终止本轮，等下一调度
        while ((batch = sysAttachmentService.cleanupOrphanAttachments(BATCH_SIZE)) > 0) {
            total += batch;
        }
        log.info("[附件孤儿回收] 本次共回收 {} 条附件", total);
    }
}

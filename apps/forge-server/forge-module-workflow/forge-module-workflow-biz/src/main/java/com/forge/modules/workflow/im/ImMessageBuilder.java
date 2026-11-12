package com.forge.modules.workflow.im;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IM 消息文案与跳转链接构造器
 * <p>
 * 文案：<br>
 * · 新待办审批（task create/recreate）→ 待办页<br>
 * · 审批超时提醒（task timeout）→ 待办页<br>
 * · 流程审批结果（实例 7 终态）→ 实例列表
 * <p>
 * 跳转链接 = sys_config im.notify.web-base-url + 既有前端路由（菜单驱动：
 * /workflow/task/todo、/workflow/instance），附 taskId/instanceId query 供后续深链（v1 前端不处理）。
 *
 * @author forge-admin
 */
@Component
public class ImMessageBuilder {

    private static final int TITLE_MAX = 50;
    private static final int CONTENT_MAX = 200;

    private final ImConfigReader configReader;

    public ImMessageBuilder(ImConfigReader configReader) {
        this.configReader = configReader;
    }

    /**
     * 任务事件消息（create/recreate/timeout）
     */
    public ImNotificationMessage buildTaskMessage(TaskEventType eventType, FlwTask task,
                                                  String processKey, String processName) {
        String processLabel = StringUtils.hasText(processName) ? processName : "审批流程";
        String title;
        String content;
        if (eventType == TaskEventType.timeout) {
            title = "审批超时提醒";
            content = "任务「" + task.getTaskName() + "」（" + processLabel + "）即将超时，请尽快处理";
        } else {
            title = "新待办审批";
            content = "「" + processLabel + "」· " + task.getTaskName() + "，请及时处理";
        }
        String url = configReader.webBaseUrl() + "/workflow/task/todo?taskId=" + task.getId();
        return new ImNotificationMessage(eventType == TaskEventType.timeout ? "taskTimeout" : "taskCreated",
                truncate(title, TITLE_MAX), truncate(content, CONTENT_MAX),
                url, task.getId(), task.getInstanceId(), processKey);
    }

    /**
     * 实例终态消息（通知发起人）
     */
    public ImNotificationMessage buildInstanceMessage(InstanceEventType eventType, FlwHisInstance instance,
                                                      String processKey, String processName) {
        String processLabel = StringUtils.hasText(processName) ? processName : "审批流程";
        String title = "流程审批结果";
        String content = "您的流程「" + processLabel + "」" + stateText(eventType);
        String url = configReader.webBaseUrl() + "/workflow/instance?instanceId=" + instance.getId();
        return new ImNotificationMessage("instanceFinished",
                truncate(title, TITLE_MAX), truncate(content, CONTENT_MAX),
                url, null, instance.getId(), processKey);
    }

    /**
     * 终态 → 结果文案（与 BusinessCallbackEventListener 终态集一致）
     * <p>
     * forceComplete 在 FlowLong 1.2.7 引擎内无发射方（RuntimeServiceImpl 终态仅传
     * reject/revoke/timeout/complete 等事件），命中 default 兜底文案，不单列 case
     */
    private String stateText(InstanceEventType eventType) {
        return switch (eventType) {
            case end -> "已审批通过";
            case autoComplete -> "已自动通过（超时免审）";
            case autoReject -> "已自动驳回（超时未审）";
            case rejectComplete -> "已被驳回";
            case revokeComplete -> "已被发起人撤销";
            case timeoutComplete -> "已超时结束";
            default -> "已结束";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

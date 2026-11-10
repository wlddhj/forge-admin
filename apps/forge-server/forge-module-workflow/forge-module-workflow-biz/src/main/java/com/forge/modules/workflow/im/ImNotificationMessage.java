package com.forge.modules.workflow.im;

/**
 * IM 通知消息载体（与渠道无关）
 *
 * @param eventType   事件类型标识（taskCreated/taskTimeout/instanceFinished）
 * @param title       标题（已截断）
 * @param content     内容（已截断）
 * @param url         点击跳转链接（可空）
 * @param taskId      任务ID（模式 B 回写关联预留，可空）
 * @param instanceId  流程实例ID（模式 B 回写关联预留）
 * @param processKey  流程 Key（可空）
 * @author forge-admin
 */
public record ImNotificationMessage(String eventType, String title, String content, String url,
                                    Long taskId, Long instanceId, String processKey) {
}

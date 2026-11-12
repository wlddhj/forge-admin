package com.forge.modules.workflow.listener.event;

import java.io.Serializable;
import java.util.List;

/**
 * 站内任务通知事件（评审 §4.4-W1-P1#3 及扩展）：
 * 事务内发布（WfUrgeService 催办 / TaskNotificationListener 待办创建·完成·撤回·
 * 超时·自动办理通知），AFTER_COMMIT 监听器提交后发送 WebSocket——回滚不产生
 * 幽灵通知、慢发送不占用事务线程。内容为发布时刻烘焙的不可变快照。
 *
 * @author forge-admin
 */
public record TaskNotificationEvent(Long taskId, Long instanceId, List<Long> userIds,
                                    String title, String content) implements Serializable {
}

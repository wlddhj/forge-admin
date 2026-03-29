package com.forge.admin.common.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * WebSocket 通知消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {

    /**
     * 消息类型: notice-公告, system-系统消息
     */
    private String type;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 关联ID（如公告ID）
     */
    private Long relatedId;

    /**
     * 时间戳
     */
    private Long timestamp;

    public static NotificationMessage notice(String title, String content, Long noticeId) {
        return new NotificationMessage("notice", title, content, noticeId, System.currentTimeMillis());
    }

    public static NotificationMessage system(String title, String content) {
        return new NotificationMessage("system", title, content, null, System.currentTimeMillis());
    }
}

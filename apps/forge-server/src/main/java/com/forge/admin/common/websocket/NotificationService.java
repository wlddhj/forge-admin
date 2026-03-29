package com.forge.admin.common.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 通知推送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 向所有在线用户广播通知
     */
    public void broadcast(NotificationMessage message) {
        log.info("[WebSocket] 广播通知: type={}, title={}", message.getType(), message.getTitle());
        messagingTemplate.convertAndSend("/topic/notifications", message);
    }

    /**
     * 向指定用户推送通知
     */
    public void sendToUser(Long userId, NotificationMessage message) {
        log.info("[WebSocket] 推送通知给用户 {}: {}", userId, message.getTitle());
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", message);
    }

    /**
     * 广播新公告
     */
    public void broadcastNotice(String title, String content, Long noticeId) {
        broadcast(NotificationMessage.notice(title, content, noticeId));
    }
}

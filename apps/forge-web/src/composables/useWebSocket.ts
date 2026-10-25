import { ref } from 'vue'
import SockJS from 'sockjs-client'
import { Client, IMessage } from '@stomp/stompjs'
import { useUserStore } from '@/stores/user'
import { ElNotification } from 'element-plus'

export interface WsNotification {
  type: string
  title: string
  content: string
  relatedId?: number
  timestamp: number
}

const stompClient = ref<Client | null>(null)
const connected = ref(false)
const notifications = ref<WsNotification[]>([])
const unreadCount = ref(0)

/** 页面级事件订阅（按 type/relatedId 过滤消费），组件卸载须调用注销函数 */
type NotificationHandler = (notification: WsNotification) => void
const handlers = new Set<NotificationHandler>()

const dispatch = (notification: WsNotification) => {
  handlers.forEach(handler => {
    try {
      handler(notification)
    } catch (e) {
      console.error('[WebSocket] notification handler error:', e)
    }
  })
}

export function useWebSocket() {
  const userStore = useUserStore()

  const connect = () => {
    if (stompClient.value?.connected) return

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    })

    client.onConnect = () => {
      connected.value = true
      // 订阅全局通知
      client.subscribe('/topic/notifications', (message: IMessage) => {
        const notification: WsNotification = JSON.parse(message.body)
        handleNotification(notification)
      })
      // 订阅个人通知
      if (userStore.userInfo?.userId) {
        client.subscribe(
          `/user/${userStore.userInfo.userId}/queue/notifications`,
          (message: IMessage) => {
            const notification: WsNotification = JSON.parse(message.body)
            handleNotification(notification)
          }
        )
      }
    }

    client.onDisconnect = () => {
      connected.value = false
    }

    client.onStompError = (frame) => {
      console.error('[WebSocket] STOMP error:', frame.headers?.message || frame.body)
    }

    client.activate()
    stompClient.value = client
  }

  const disconnect = () => {
    if (stompClient.value) {
      stompClient.value.deactivate()
      stompClient.value = null
      connected.value = false
    }
  }

  const handleNotification = (notification: WsNotification) => {
    // 页面级消费者先分发（如对话页实时刷新审批气泡）
    dispatch(notification)
    // 纯页面态类型不进通知中心（AGENT_RUN_STATUS/AGENT_RUN_EVENT 仅为对话页实时状态与过程帧）
    if (notification.type === 'AGENT_RUN_STATUS' || notification.type === 'AGENT_RUN_EVENT') {
      return
    }
    notifications.value.unshift(notification)
    unreadCount.value++

    ElNotification({
      title: notification.title,
      message: notification.content || '您有一条新通知',
      type: notification.type === 'notice' ? 'info' : 'warning',
      duration: 5000,
    })
  }

  /**
   * 注册页面级通知监听，返回注销函数（组件 onUnmounted 调用，防泄漏）
   */
  const onNotification = (handler: NotificationHandler) => {
    handlers.add(handler)
    return () => {
      handlers.delete(handler)
    }
  }

  const markAllRead = () => {
    unreadCount.value = 0
  }

  const clearNotifications = () => {
    notifications.value = []
    unreadCount.value = 0
  }

  return {
    connected,
    notifications,
    unreadCount,
    connect,
    disconnect,
    markAllRead,
    clearNotifications,
    onNotification,
  }
}

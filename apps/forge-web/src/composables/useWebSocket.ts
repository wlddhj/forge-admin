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

export function useWebSocket() {
  const userStore = useUserStore()

  const connect = () => {
    if (stompClient.value?.connected) return

    const client = new Client({
      webSocketFactory: () => new SockJS('/api/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: false,
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
    notifications.value.unshift(notification)
    unreadCount.value++

    ElNotification({
      title: notification.title,
      message: notification.content || '您有一条新通知',
      type: notification.type === 'notice' ? 'info' : 'warning',
      duration: 5000,
    })
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
  }
}

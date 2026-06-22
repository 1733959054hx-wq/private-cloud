import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

type MessageCallback = (message: any) => void

let stompClient: Client | null = null
let currentUserId: number | null = null
let currentCallback: MessageCallback | null = null
const subscriptions = new Map<string, string>()

function doSubscribe() {
  if (!stompClient?.active || !currentUserId || !currentCallback) return

  const userQueueKey = `user-queue-${currentUserId}`
  if (!subscriptions.has(userQueueKey)) {
    const sub = stompClient.subscribe(`/user/queue/notifications`, (msg: IMessage) => {
      try {
        currentCallback!(JSON.parse(msg.body))
      } catch { /* ignore */ }
    })
    subscriptions.set(userQueueKey, sub.id)
  }

  const mentionKey = `mention-${currentUserId}`
  if (!subscriptions.has(mentionKey)) {
    const sub = stompClient.subscribe(`/topic/mention/${currentUserId}`, (msg: IMessage) => {
      try {
        currentCallback!(JSON.parse(msg.body))
      } catch { /* ignore */ }
    })
    subscriptions.set(mentionKey, sub.id)
  }
}

export function connectWebSocket(userId: number, onNotification?: MessageCallback) {
  currentUserId = userId
  if (onNotification) {
    currentCallback = onNotification
  }

  if (stompClient?.active) {
    doSubscribe()
    return
  }

  const token = sessionStorage.getItem('token')

  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 30000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    onConnect: () => {
      doSubscribe()
    },
    onStompError: (frame: any) => {
      console.error('STOMP error:', frame.headers?.message || frame.body)
    },
    onWebSocketClose: () => {
      // 静默处理连接关闭，不输出到控制台
    },
  })

  stompClient.activate()
}

export function disconnectWebSocket() {
  if (stompClient?.active) {
    stompClient.deactivate()
  }
  subscriptions.clear()
  stompClient = null
  currentUserId = null
  currentCallback = null
}

export function isConnected(): boolean {
  return stompClient?.active ?? false
}
import { Client } from '@stomp/stompjs'
import type { WsTaskEvent } from '@/types'

type TaskEventHandler = (event: WsTaskEvent) => void

let client: Client | null = null
const handlers: TaskEventHandler[] = []

/**
 * 初始化并连接 WebSocket（应用启动时调用一次）
 */
export function initWsClient(): void {
  const wsUrl = import.meta.env.VITE_WS_URL || `ws://${location.host}/ws`

  client = new Client({
    brokerURL: wsUrl,
    reconnectDelay: 5000,
    onConnect: () => {
      console.log('[WS] Connected')
      client!.subscribe('/topic/tasks', (message) => {
        try {
          const event: WsTaskEvent = JSON.parse(message.body)
          handlers.forEach((h) => h(event))
        } catch (e) {
          console.error('[WS] Failed to parse message', e)
        }
      })
    },
    onDisconnect: () => {
      console.log('[WS] Disconnected')
    },
    onStompError: (frame) => {
      console.error('[WS] STOMP error', frame)
    },
  })

  client.activate()
}

/**
 * 注册任务事件监听器
 */
export function onTaskEvent(handler: TaskEventHandler): () => void {
  handlers.push(handler)
  // 返回取消注册函数
  return () => {
    const idx = handlers.indexOf(handler)
    if (idx !== -1) handlers.splice(idx, 1)
  }
}

/**
 * 断开连接（应用卸载时调用）
 */
export function disconnectWs(): void {
  client?.deactivate()
}

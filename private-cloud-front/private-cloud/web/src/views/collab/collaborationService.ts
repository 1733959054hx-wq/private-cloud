import { QuillBinding } from 'y-quill'
import { WebsocketProvider } from 'y-websocket'
import * as Y from 'yjs'
import type Quill from 'quill'

export interface CollabOptions {
  /** Yjs WebSocket 服务地址 */
  wsUrl: string
  /** 文档/房间 ID */
  docId: string
  /** 当前用户名称（用于光标显示） */
  userName?: string
  /** Quill 编辑器实例 */
  quill: Quill
  /** 是否自动连接（默认 true，设为 false 后可手动 connect） */
  autoConnect?: boolean
}

export interface CollabInstance {
  ydoc: Y.Doc
  provider: WebsocketProvider
  destroy: () => void
  /** 手动连接（autoConnect=false 时使用） */
  connect: () => void
}

/**
 * 协同编辑服务
 * 封装 Yjs + y-websocket + y-quill 的绑定逻辑
 */
export class CollaborationService {
  /**
   * 设置协同编辑
   * @returns 返回 ydoc, provider, destroy 方法
   */
  static setup(options: CollabOptions): CollabInstance {
    const { wsUrl, docId, quill, autoConnect = true } = options

    // 1. 创建 Yjs 文档
    const ydoc = new Y.Doc()

    // 2. 创建 WebSocket 提供者（先不连接，由调用方控制）
    const provider = new WebsocketProvider(wsUrl, docId, ydoc, {
      connect: autoConnect,
    })

    // 3. 获取 Yjs 共享文本类型
    const ytext = ydoc.getText('quill')

    // 4. 绑定 Quill 编辑器与 Yjs 文本
    const binding = new QuillBinding(ytext, quill, provider.awareness)

    // 5. 设置用户光标信息
    if (options.userName) {
      provider.awareness.setLocalStateField('user', {
        name: options.userName,
        color: CollaborationService.getRandomColor(),
      })
    }

    // 返回销毁方法
    return {
      ydoc,
      provider,
      destroy: () => {
        try {
          binding.destroy()
          provider.disconnect()
          ydoc.destroy()
        } catch (e) {
          console.warn('[Yjs] 销毁资源时出错:', e)
        }
      },
      connect: () => {
        provider.connect()
      },
    }
  }

  /** 生成随机颜色 */
  private static getRandomColor(): string {
    const colors = [
      '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4',
      '#FFEAA7', '#DDA0DD', '#98D8C8', '#F7DC6F',
      '#BB8FCE', '#85C1E9', '#F0B27A', '#82E0AA',
    ]
    return colors[Math.floor(Math.random() * colors.length)]
  }
}

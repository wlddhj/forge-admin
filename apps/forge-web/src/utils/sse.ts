import { useUserStore } from '@/stores/user'

// SSE 事件类型
export type SSEEventType = 'message' | 'error' | 'done'

// SSE 事件回调
export interface SSECallbacks {
  onMessage?: (data: string) => void
  onError?: (error: Error) => void
  onDone?: () => void
}

// SSE 客户端类
export class SSEClient {
  private abortController: AbortController | null = null

  /**
   * POST 请求 SSE 流式连接
   * @param url API URL（不含 baseURL）
   * @param data 请求体数据
   * @param callbacks 事件回调
   */
  async connectPost(url: string, data: any, callbacks: SSECallbacks): Promise<void> {
    this.abortController = new AbortController()
    const userStore = useUserStore()

    try {
      const response = await fetch(`/admin-api${url}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${userStore.token}`,
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify(data),
        signal: this.abortController.signal
      })

      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(errorText || `HTTP ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('No response body')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()

        if (done) {
          callbacks.onDone?.()
          break
        }

        buffer += decoder.decode(value, { stream: true })

        // 解析 SSE 数据格式
        const lines = buffer.split('\n')
        buffer = ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const dataStr = line.substring(5).trim()
            if (dataStr) {
              callbacks.onMessage?.(dataStr)
            }
          } else if (line.trim() && !line.startsWith(':')) {
            // 保留不完整的行到 buffer
            buffer = line
          }
        }
      }
    } catch (error: any) {
      if (error.name === 'AbortError') {
        // 用户主动取消，不视为错误
        callbacks.onDone?.()
      } else {
        callbacks.onError?.(error)
      }
    }
  }

  /**
   * 取消 SSE 连接
   */
  abort(): void {
    if (this.abortController) {
      this.abortController.abort()
      this.abortController = null
    }
  }

  /**
   * 检查是否已连接
   */
  isConnected(): boolean {
    return this.abortController !== null
  }
}

/**
 * 创建 SSE 客户端实例
 */
export function createSSE(): SSEClient {
  return new SSEClient()
}

/**
 * 解析 SSE JSON 数据
 * @param dataStr SSE data 字段字符串
 * @returns 解析后的对象，解析失败返回 null
 */
export function parseSSEData<T = any>(dataStr: string): T | null {
  try {
    return JSON.parse(dataStr)
  } catch {
    return null
  }
}

/**
 * SSE 流式发送消息的便捷方法
 * @param url API URL
 * @param data 请求体
 * @param onChunk 每次收到数据块的回调
 * @returns 完成后的 Promise
 */
export async function streamMessage(
  url: string,
  data: any,
  onChunk: (chunk: string) => void
): Promise<void> {
  const client = createSSE()
  return client.connectPost(url, data, {
    onMessage: (dataStr) => {
      // 尝试解析 JSON 格式的流式数据
      const parsed = parseSSEData<{ content?: string; done?: boolean }>(dataStr)
      if (parsed?.content) {
        onChunk(parsed.content)
      } else if (parsed?.done) {
        // 流结束标记
        return
      } else {
        // 纯文本格式，直接传递
        onChunk(dataStr)
      }
    },
    onError: (error) => {
      throw error
    }
  })
}
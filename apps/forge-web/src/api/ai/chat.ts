import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import { useUserStore } from '@/stores/user'

// 对话响应
export interface ConversationResponse {
  id: number
  title: string
  modelId: number
  modelName: string
  createTime: string
  updateTime: string
}

// 消息响应
export interface MessageResponse {
  id: number
  conversationId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  /** 思考内容（思考模型 reasoning_content，仅 assistant 消息携带） */
  reasoningContent?: string
  createTime: string
}

// 创建对话请求
export interface ConversationCreateRequest {
  title?: string
  modelId?: number
}

// 发送消息请求
export interface MessageSendRequest {
  conversationId: number
  content: string
}

// 对话查询参数
export interface ConversationQuery {
  modelId?: number
  pageNum: number
  pageSize: number
}

export const chatApi = {
  // 创建新对话
  create: (data: ConversationCreateRequest) =>
    request.post<ConversationResponse>('/ai/chat/conversation', data),

  // 获取对话列表
  list: (params: ConversationQuery) =>
    request.get<PageResult<ConversationResponse>>('/ai/chat/conversation/list', { params }),

  // 删除对话
  delete: (id: number) =>
    request.delete(`/ai/chat/conversation/${id}`),

  // 获取对话消息列表
  messages: (conversationId: number) =>
    request.get<MessageResponse[]>(`/ai/chat/conversation/${conversationId}/messages`),

  // 发送消息（返回 SSE 流）
  send: (data: MessageSendRequest) =>
    request.post<MessageResponse>('/ai/chat/message', data),

  // 更新对话标题
  updateTitle: (id: number, title: string) =>
    request.put(`/ai/chat/conversation/${id}/title`, { title }),

  // 保存AI回复消息（含思考内容）
  saveAiMessage: (conversationId: number, content: string, reasoningContent?: string) =>
    request.post(`/ai/chat/message/save-ai?conversationId=${conversationId}`, { content, reasoningContent }, {
      headers: { 'Content-Type': 'application/json' }
    })
}

// 导出独立函数
export const createConversation = (data: ConversationCreateRequest) =>
  chatApi.create(data).then(res => res.data)
export const getConversationList = (params: ConversationQuery) =>
  chatApi.list(params).then(res => res.data)
export const deleteConversation = (id: number) => chatApi.delete(id)
export const getConversationMessages = (conversationId: number) =>
  chatApi.messages(conversationId).then(res => res.data)
export const sendMessage = (data: MessageSendRequest) =>
  chatApi.send(data).then(res => res.data)
export const updateConversationTitle = (id: number, title: string) =>
  chatApi.updateTitle(id, title)
export const saveAiMessage = (conversationId: number, content: string, reasoningContent?: string) =>
  chatApi.saveAiMessage(conversationId, content, reasoningContent)

/**
 * AI 生成会话标题。返回新标题；无需生成或失败时返回 null。
 * 走原生 fetch 而非 axios 实例：标题生成是后台增强，失败必须静默，
 * 而 axios 响应拦截器会统一弹错且有 30s 超时（LLM 慢时会误报）。
 */
export const generateConversationTitle = async (id: number, content: string): Promise<string | null> => {
  const userStore = useUserStore()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${userStore.token}`
  }
  if (userStore.tenantId !== null && userStore.tenantId !== undefined) {
    headers['X-Tenant-Id'] = String(userStore.tenantId)
  }
  const response = await fetch(`/admin-api/ai/chat/conversation/${id}/generate-title`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ content })
  })
  if (!response.ok) return null
  const res = await response.json()
  return typeof res?.data === 'string' && res.data ? res.data : null
}
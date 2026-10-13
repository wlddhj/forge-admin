import request from '@/utils/request'

/** Agent 事件帧（SSE） */
export interface AgentEventFrame {
  event: 'STEP_START' | 'STEP_END' | 'TOOL_CALL' | 'TOOL_RESULT' | 'APPROVAL_REQUIRED' | 'FINAL' | 'ERROR'
  node: string | null
  name: string
  data: Record<string, any>
}

/** Agent 配置 */
export interface AgentConfig {
  id: number
  agentCode: string
  agentName: string
  graphKey: string
  description: string | null
  systemPrompt: string | null
  userPromptTemplate: string | null
  modelBindings: string | null
  maxSteps: number
  status: number
  remark: string | null
  createTime: string
  updateTime: string
}

/** Agent 运行记录 */
export interface AgentRun {
  id: number
  agentId: number
  conversationId: number | null
  userId: number
  triggerType: 'CONVERSATION' | 'MANUAL' | 'SCHEDULED'
  status: 'RUNNING' | 'WAITING_APPROVAL' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  lastNode: string | null
  errorMsg: string | null
  inputSummary: string | null
  startTime: string
  endTime: string | null
}

/** Agent 运行步骤 */
export interface AgentRunStep {
  id: number
  runId: number
  seq: number
  nodeName: string
  stepType: string
  modelId: number | null
  toolName: string | null
  stepInput: string | null
  stepOutput: string | null
  tokensIn: number
  tokensOut: number
  durationMs: number
  status: string
  errorMsg: string | null
  createTime: string
}

/** Agent 人工待办 */
export interface AgentTodo {
  id: number
  runId: number
  title: string
  content: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  handlerId: number | null
  handlerName: string | null
  handleComment: string | null
  handleTime: string | null
  createTime: string
}

/** Agent 定时任务 */
export interface AgentJob {
  id: number
  jobName: string
  agentCode: string
  sysJobId: number | null
  inputTemplate: string | null
  cronExpression: string
  status: number
  lastRunId: number | null
  createTime: string
  updateTime: string
}

export const agentJobApi = {
  page: (params: { jobName?: string; agentCode?: string; status?: number; pageNum: number; pageSize: number }) =>
    request.get<{ records: AgentJob[]; total: number }>('/ai/agent/job/list', { params }),

  add: (data: Partial<AgentJob>) => request.post('/ai/agent/job', data),

  update: (id: number, data: Partial<AgentJob>) => request.put(`/ai/agent/job/${id}`, data),

  changeStatus: (id: number, status: number) =>
    request.put(`/ai/agent/job/${id}/status`, null, { params: { status } }),

  remove: (id: number) => request.delete(`/ai/agent/job/${id}`),

  runOnce: (id: number) => request.post<AgentRun>(`/ai/agent/job/${id}/run`)
}

export const agentApi = {
  listConfigs: (status?: number) =>
    request.get<AgentConfig[]>('/ai/agent/config/list', { params: status ? { status } : {} }),

  updateConfig: (id: number, data: Partial<AgentConfig>) =>
    request.put(`/ai/agent/config/${id}`, data),

  pageRuns: (params: { agentId?: number; status?: string; pageNum: number; pageSize: number }) =>
    request.get<{ records: AgentRun[]; total: number }>('/ai/agent/run/list', { params }),

  getSteps: (runId: number) =>
    request.get<AgentRunStep[]>(`/ai/agent/run/${runId}/steps`),

  pageTodos: (params: { status?: string; pageNum: number; pageSize: number }) =>
    request.get<{ records: AgentTodo[]; total: number }>('/ai/agent/todo/list', { params }),

  approveTodo: (id: number, comment?: string) =>
    request.post<AgentRun>(`/ai/agent/todo/${id}/approve`, { comment }),

  rejectTodo: (id: number, comment?: string) =>
    request.post<AgentRun>(`/ai/agent/todo/${id}/reject`, { comment })
}

export const getAgentConfigs = (status?: number) => agentApi.listConfigs(status).then(res => res.data)
export const getAgentRuns = (params: { agentId?: number; status?: string; pageNum: number; pageSize: number }) =>
  agentApi.pageRuns(params).then(res => res.data)
export const getAgentSteps = (runId: number) => agentApi.getSteps(runId).then(res => res.data)
export const getAgentTodos = (params: { status?: string; pageNum: number; pageSize: number }) =>
  agentApi.pageTodos(params).then(res => res.data)

/** Agent 对话 SSE：onEvent 收到解析后的事件帧 */
export const streamAgentChat = (
  body: { agentCode: string; conversationId?: number; content: string; images?: string[] },
  onEvent: (frame: AgentEventFrame) => void,
  onError?: (message: string) => void
) => {
  const token = localStorage.getItem('token') || ''
  return fetch('/admin-api/ai/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'X-Tenant-Id': localStorage.getItem('tenantId') || '1'
    },
    body: JSON.stringify(body)
  }).then(async (response) => {
    if (!response.ok || !response.body) {
      onError?.(`请求失败: ${response.status}`)
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) continue
        const payload = trimmed.slice(5).trim()
        if (!payload) continue
        try {
          onEvent(JSON.parse(payload))
        } catch {
          // 忽略无法解析的帧
        }
      }
    }
  }).catch((e) => onError?.(String(e)))
}

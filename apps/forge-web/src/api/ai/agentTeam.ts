import request from '@/utils/request'
import type { AgentConfig } from './agent'

/** Agent 团队成员 */
export interface TeamMember {
  agentId: number
  agentCode?: string
  agentName?: string
  memberRole: string
  allowedTools?: string[]
  maxSteps?: number | null
  sortOrder?: number
}

/** Agent 团队 */
export interface AgentTeam {
  id: number
  teamCode: string
  teamName: string
  description: string | null
  supervisorPrompt: string
  modelBindings: string | null
  maxRounds: number
  status: number
  remark: string | null
  createTime: string
  members: TeamMember[]
}

/** 团队保存请求（新增/编辑同构） */
export interface TeamSaveRequest {
  teamCode: string
  teamName: string
  description?: string
  supervisorPrompt: string
  modelBindings?: string
  maxRounds: number
  status?: number
  remark?: string
  members: TeamMember[]
}

export const agentTeamApi = {
  list: (status?: number) =>
    request.get<AgentTeam[]>('/ai/team/list', { params: status ? { status } : {} }),

  get: (id: number) => request.get<AgentTeam>(`/ai/team/${id}`),

  add: (data: TeamSaveRequest) => request.post('/ai/team', data),

  update: (id: number, data: TeamSaveRequest) => request.put(`/ai/team/${id}`, data),

  remove: (ids: number[]) => request.delete('/ai/team/' + ids.join(','))
}

export const getAgentTeams = (status?: number) => agentTeamApi.list(status).then(res => res.data)
export const getAgentTeam = (id: number) => agentTeamApi.get(id).then(res => res.data)

/** 工具常量（与后端 ToolRegistry 注册名对齐） */
export const AGENT_TOOLS = [
  { value: 'sql_query', label: 'SQL 查询（高危）' },
  { value: 'http_fetch', label: 'HTTP 抓取' }
]

/** 成员可选项（从启用的 Agent 配置生成） */
export const memberAgentOptions = (configs: AgentConfig[]) =>
  configs.map(c => ({ value: c.id, label: `${c.agentName}（${c.agentCode}）` }))

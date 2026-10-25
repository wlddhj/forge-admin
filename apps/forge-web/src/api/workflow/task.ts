import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { TaskInfo, TaskQuery, TaskApproveRequest, TaskDelegateRequest, TaskTransferRequest, TaskReturnRequest } from '@/types/workflow'

export interface TaskSignCreateRequest {
  /** before 前加签 / after 后加签 / parallel 并行加签 */
  type: 'before' | 'after' | 'parallel'
  userIds: string[]
  /** 多人审批方式（仅 before/after 生效）：1 依次 / 2 会签（默认）/ 3 或签 */
  examineMode?: number
  reason?: string
}

export interface TaskSignDeleteRequest {
  /** 被减签的用户ID */
  actorId: string
  reason?: string
}

export const taskApi = {
  todo: (params: TaskQuery) =>
    request.get<PageResult<TaskInfo>>('/workflow/task/todo', { params }).then(res => res.data),
  claimable: (params: TaskQuery) =>
    request.get<PageResult<TaskInfo>>('/workflow/task/claimable', { params }).then(res => res.data),
  done: (params: TaskQuery) =>
    request.get<PageResult<TaskInfo>>('/workflow/task/done', { params }).then(res => res.data),
  cc: (params: TaskQuery) =>
    request.get<PageResult<TaskInfo>>('/workflow/task/cc', { params }).then(res => res.data),
  ccByInstanceId: (processInstanceId: string) =>
    request.get<TaskInfo[]>(`/workflow/task/cc/${processInstanceId}`).then(res => res.data),
  getById: (id: string) =>
    request.get<TaskInfo>(`/workflow/task/${id}`).then(res => res.data),
  claim: (id: string) =>
    request.post(`/workflow/task/${id}/claim`),
  unclaim: (id: string) =>
    request.post(`/workflow/task/${id}/unclaim`),
  approve: (id: string, data: TaskApproveRequest) =>
    request.post(`/workflow/task/${id}/approve`, data),
  reject: (id: string, data: TaskApproveRequest) =>
    request.post(`/workflow/task/${id}/reject`, data),
  delegate: (id: string, data: TaskDelegateRequest) =>
    request.post(`/workflow/task/${id}/delegate`, data),
  transfer: (id: string, data: TaskTransferRequest) =>
    request.post(`/workflow/task/${id}/transfer`, data),
  return: (id: string, data: TaskReturnRequest) =>
    request.post(`/workflow/task/${id}/return`, data),
  getReturnNodes: (id: string) =>
    request.get(`/workflow/task/${id}/return-nodes`).then(res => res.data),
  signCreate: (id: string, data: TaskSignCreateRequest) =>
    request.post(`/workflow/task/${id}/sign-create`, data),
  signDelete: (id: string, data: TaskSignDeleteRequest) =>
    request.post(`/workflow/task/${id}/sign-delete`, data),
  copy: (id: string, data: { copyUserIds: number[]; reason?: string }) =>
    request.post(`/workflow/task/${id}/copy`, data),
  withdraw: (id: string) =>
    request.post(`/workflow/task/${id}/withdraw`),
  getChildTasks: (id: string) =>
    request.get(`/workflow/task/${id}/child-tasks`).then(res => res.data)
}

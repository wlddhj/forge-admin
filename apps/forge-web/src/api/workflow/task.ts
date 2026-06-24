import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { TaskInfo, TaskQuery, TaskApproveRequest, TaskDelegateRequest, TaskTransferRequest, TaskReturnRequest } from '@/types/workflow'

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
  complete: (id: string, data: TaskApproveRequest) =>
    request.post(`/workflow/task/${id}/complete`, data),
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
  signCreate: (id: string, data: { type: string; userIds: string[]; reason?: string }) =>
    request.post(`/workflow/task/${id}/sign-create`, data),
  signDelete: (id: string, data: { childTaskId: string; reason?: string }) =>
    request.post(`/workflow/task/${id}/sign-delete`, data),
  copy: (id: string, data: { copyUserIds: number[]; reason?: string }) =>
    request.post(`/workflow/task/${id}/copy`, data),
  withdraw: (id: string) =>
    request.post(`/workflow/task/${id}/withdraw`),
  getChildTasks: (id: string) =>
    request.get(`/workflow/task/${id}/child-tasks`).then(res => res.data)
}

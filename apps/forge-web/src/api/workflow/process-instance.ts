import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ProcessInstance, ProcessInstanceQuery, ProcessStartRequest, ApprovalComment } from '@/types/workflow'

export const processInstanceApi = {
  page: (params: ProcessInstanceQuery) =>
    request.get<PageResult<ProcessInstance>>('/workflow/instance/list', { params }).then(res => res.data),
  myInstances: (params: ProcessInstanceQuery) =>
    request.get<PageResult<ProcessInstance>>('/workflow/instance/my', { params }).then(res => res.data),
  getById: (id: string) =>
    request.get<ProcessInstance>(`/workflow/instance/${id}`).then(res => res.data),
  start: (data: ProcessStartRequest) =>
    request.post('/workflow/instance/start', data).then(res => res.data),
  cancel: (id: string) =>
    request.delete(`/workflow/instance/${id}`).then(res => res.data),
  getDiagram: (id: string) =>
    request.get(`/workflow/instance/${id}/diagram`, { responseType: 'blob' }),
  getComments: (id: string) =>
    request.get<ApprovalComment[]>(`/workflow/instance/${id}/comments`).then(res => res.data),
  getVariables: (id: string) =>
    request.get<Record<string, any>>(`/workflow/instance/${id}/variables`).then(res => res.data),
  getApprovalDetail: (id: string) =>
    request.get(`/workflow/instance/${id}/approval-detail`).then(res => res.data)
}

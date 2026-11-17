import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ProcessInstance, ProcessInstanceQuery, ProcessStartRequest, ApprovalComment, ApprovalDetail, AiDecisionRecords, BizViewData } from '@/types/workflow'

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
    request.getBlob(`/workflow/instance/${id}/diagram`, { responseType: 'blob' }),
  getComments: (id: string) =>
    request.get<ApprovalComment[]>(`/workflow/instance/${id}/comments`).then(res => res.data),
  getVariables: (id: string) =>
    request.get<Record<string, any>>(`/workflow/instance/${id}/variables`).then(res => res.data),
  getApprovalDetail: (id: string) =>
    request.get<ApprovalDetail>(`/workflow/instance/${id}/approval-detail`).then(res => res.data),
  getAiDecisions: (id: string) =>
    request.get<AiDecisionRecords>(`/workflow/instance/${id}/ai-decisions`).then(res => res.data),
  urge: (id: string, data?: { comment?: string }) =>
    request.post(`/workflow/instance/${id}/urge`, data || {}),
  copy: (id: string, data: { copyUserIds: number[]; reason?: string }) =>
    request.post(`/workflow/instance/${id}/copy`, data),
  resume: (id: string) =>
    request.post(`/workflow/instance/${id}/resume`),
  /** 业务单据视图（formType=20，实例参与人鉴权） */
  getBizView: (id: string) =>
    request.get<BizViewData>(`/workflow/instance/${id}/biz-view`).then(res => res.data),
  /** 业务单据预览（如合同 PDF，返回 blob） */
  getBizPreview: (id: string) =>
    request.getBlob(`/workflow/instance/${id}/biz-preview`),
  /** 业务单据附件下载（附件须属于该实例单据） */
  downloadBizAttachment: (id: string, attachmentId: number) =>
    request.getBlob(`/workflow/instance/${id}/biz-attachment/${attachmentId}`)
}

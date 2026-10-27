import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { BusinessCallback, BusinessCallbackRecord } from '@/types/workflow'

export interface CallbackRequest {
  id?: number
  name: string
  url: string
  secret?: string
  events: string[]
  processKey?: string
  includeVariables?: number
  enabled?: number
  timeoutMs?: number
}

export const callbackApi = {
  page: (params: { pageNum: number; pageSize: number; name?: string; enabled?: number }) =>
    request.get<PageResult<BusinessCallback>>('/workflow/callback', { params }).then(res => res.data),
  create: (data: CallbackRequest) =>
    request.post('/workflow/callback', data),
  update: (data: CallbackRequest) =>
    request.put('/workflow/callback', data),
  remove: (id: number) =>
    request.delete(`/workflow/callback/${id}`),
  records: (id: number, params: { pageNum: number; pageSize: number; status?: string }) =>
    request.get<PageResult<BusinessCallbackRecord>>(`/workflow/callback/${id}/records`, { params }).then(res => res.data),
  resend: (recordId: number) =>
    request.post(`/workflow/callback/record/${recordId}/resend`)
}

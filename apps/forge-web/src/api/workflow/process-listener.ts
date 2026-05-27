import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface ProcessListener {
  id: number
  name: string
  status: number
  type: string
  event: string
  valueType: string
  value: string
  remark: string
  createTime: string
  updateTime: string
}

export interface ListenerQuery {
  pageNum: number
  pageSize: number
  name?: string
  type?: string
  status?: number
}

export interface ListenerRequest {
  id?: number
  name: string
  status?: number
  type: string
  event: string
  valueType: string
  value: string
  remark?: string
}

export const listenerApi = {
  page: (params: ListenerQuery) =>
    request.get<PageResult<ProcessListener>>('/workflow/listener/list', { params }).then(res => res.data),
  listAll: () =>
    request.get<ProcessListener[]>('/workflow/listener/all').then(res => res.data),
  getById: (id: number) =>
    request.get<ProcessListener>(`/workflow/listener/${id}`).then(res => res.data),
  add: (data: ListenerRequest) =>
    request.post('/workflow/listener', data),
  update: (data: ListenerRequest) =>
    request.put('/workflow/listener', data),
  delete: (ids: number[]) =>
    request.delete('/workflow/listener', { data: ids }),
}

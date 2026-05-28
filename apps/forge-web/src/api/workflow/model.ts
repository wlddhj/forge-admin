import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface WfModel {
  id: string
  name: string
  key: string
  category: string
  description: string
  version: string
  metaInfo: string
  createTime: string
  lastUpdateTime: string
  deployed: boolean
  formType: number | null
  formId: number | null
  autoCopyStrategy: number | null
  autoCopyParam: string | null
  bpmnXml: string | null
}

export interface ModelQuery {
  pageNum: number
  pageSize: number
  name?: string
  key?: string
  category?: string
}

export interface ModelRequest {
  id?: string
  name: string
  key: string
  category?: string
  description?: string
  metaInfo?: string
  bpmnXml?: string
}

export const modelApi = {
  page: (params: ModelQuery) =>
    request.get<PageResult<WfModel>>('/workflow/model/list', { params }).then(res => res.data),
  getById: (id: string) =>
    request.get<WfModel>(`/workflow/model/${id}`).then(res => res.data),
  add: (data: ModelRequest) =>
    request.post('/workflow/model', data),
  update: (data: ModelRequest) =>
    request.put('/workflow/model', data),
  deploy: (id: string) =>
    request.post(`/workflow/model/${id}/deploy`),
  delete: (id: string) =>
    request.delete(`/workflow/model/${id}`),
}

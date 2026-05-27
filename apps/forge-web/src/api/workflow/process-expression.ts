import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface ProcessExpression {
  id: number
  name: string
  status: number
  expression: string
  remark: string
  createTime: string
  updateTime: string
}

export interface ExpressionQuery {
  pageNum: number
  pageSize: number
  name?: string
  status?: number
}

export interface ExpressionRequest {
  id?: number
  name: string
  status?: number
  expression: string
  remark?: string
}

export const expressionApi = {
  page: (params: ExpressionQuery) =>
    request.get<PageResult<ProcessExpression>>('/workflow/expression/list', { params }).then(res => res.data),
  listAll: () =>
    request.get<ProcessExpression[]>('/workflow/expression/all').then(res => res.data),
  getById: (id: number) =>
    request.get<ProcessExpression>(`/workflow/expression/${id}`).then(res => res.data),
  add: (data: ExpressionRequest) =>
    request.post('/workflow/expression', data),
  update: (data: ExpressionRequest) =>
    request.put('/workflow/expression', data),
  delete: (ids: number[]) =>
    request.delete('/workflow/expression', { data: ids }),
}

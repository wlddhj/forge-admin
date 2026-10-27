import request from '@/utils/request'
import type { WfCategory, CategoryQuery, CategoryRequest } from '@/types/workflow'

export const categoryApi = {
  list: (params: CategoryQuery) =>
    request.get<WfCategory[]>('/workflow/category/list', { params }).then(res => res.data),
  listAll: () =>
    request.get<WfCategory[]>('/workflow/category/all').then(res => res.data),
  getById: (id: number) =>
    request.get<WfCategory>(`/workflow/category/${id}`).then(res => res.data),
  add: (data: CategoryRequest) =>
    request.post('/workflow/category', data),
  update: (data: CategoryRequest) =>
    request.put('/workflow/category', data),
  delete: (ids: number[]) =>
    request.delete('/workflow/category', { data: ids })
}

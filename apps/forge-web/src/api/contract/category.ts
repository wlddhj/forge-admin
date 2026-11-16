import request from '@/utils/request'
import type { CategoryNode } from '@/types/contract'

/**
 * 合同分类 API（树形）
 */
export const categoryApi = {
  /** 分类树（status 筛选） */
  tree: (status?: number) =>
    request.get<CategoryNode[]>('/contract/category/list', { params: { status } }).then(res => res.data),

  /** 全量树（表单下拉） */
  treeAll: () =>
    request.get<CategoryNode[]>('/contract/category/all').then(res => res.data),

  create: (data: { categoryName: string; categoryCode: string; parentId?: string; sortOrder?: number; status?: number; remark?: string }) =>
    request.post<string>('/contract/category', data).then(res => res.data),

  update: (data: { id: string; categoryName: string; categoryCode: string; parentId?: string; sortOrder?: number; status?: number; remark?: string }) =>
    request.put('/contract/category', data),

  remove: (ids: string[]) => request.delete(`/contract/category/${ids.join(',')}`)
}

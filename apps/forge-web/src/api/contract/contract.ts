import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ContractItem, PaperSizeCode } from '@/types/contract'

/**
 * 合同 API
 */
export const contractApi = {
  page: (params: {
    pageNum: number
    pageSize: number
    title?: string
    contractNo?: string
    categoryId?: string
    status?: number
  }) => request.get<PageResult<ContractItem>>('/contract/contract/list', { params }).then(res => res.data),

  detail: (id: string) =>
    request.get<ContractItem>(`/contract/contract/${id}`).then(res => res.data),

  create: (data: { templateId: string; title: string; fieldValues?: Record<string, unknown> }) =>
    request.post<string>('/contract/contract', data).then(res => res.data),

  update: (data: { id: string; title: string; fieldValues?: Record<string, unknown> }) =>
    request.put('/contract/contract', data),

  remove: (id: string) => request.delete(`/contract/contract/${id}`),

  /** 实时预览 PDF（草稿态） */
  previewPdf: (id: string) =>
    request.post<Blob>(`/contract/contract/${id}/preview`, undefined, { responseType: 'blob' }) as unknown as Promise<Blob>,

  /** 打印 PDF（按所选纸张重渲染；拦截器对 blob 直接回数据） */
  printPdf: (id: string, data: { paperSize?: PaperSizeCode; customWidthMm?: number; customHeightMm?: number }) =>
    request.post<Blob>(`/contract/contract/${id}/print`, data, { responseType: 'blob' }) as unknown as Promise<Blob>,

  submit: (id: string) => request.post(`/contract/contract/${id}/submit`),

  /** 导出合同清单（blob 下载） */
  export: (params: { title?: string; contractNo?: string; categoryId?: string; status?: number }) =>
    request.post<Blob>('/contract/contract/export', null, {
      params,
      responseType: 'blob'
    }) as unknown as Promise<Blob>
}

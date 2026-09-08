import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

/**
 * 打印模板响应实体
 */
export interface PrintTemplateEntity {
  id: number
  templateCode: string
  templateName: string
  contents: string
  version: number
  status: number
  remark: string | null
  tenantId: number
  createTime: string
  updateTime: string
}

/**
 * 打印模板查询参数
 */
export interface PrintTemplateQuery {
  pageNum: number
  pageSize: number
  templateCode?: string
  templateName?: string
  status?: number
}

/**
 * 打印模板新增/修改请求
 */
export interface PrintTemplateRequest {
  id?: number
  templateCode: string
  templateName: string
  contents?: string
  version?: number
  status?: number
  remark?: string
}

/**
 * 打印模板 API
 */
export const PrintTemplateApi = {
  /** 分页查询 */
  list: (params: PrintTemplateQuery) =>
    request.get<PageResult<PrintTemplateEntity>>('/system/print-template/list', { params }),

  /** 详情 */
  get: (id: number) =>
    request.get<PrintTemplateEntity>(`/system/print-template/${id}`),

  /** 按编号查询 */
  getByCode: (code: string) =>
    request.get<PrintTemplateEntity>(`/system/print-template/code/${code}`),

  /** 新增 */
  create: (data: PrintTemplateRequest) =>
    request.post<number>('/system/print-template', data),

  /** 修改 */
  update: (data: PrintTemplateRequest) =>
    request.put('/system/print-template', data),

  /** 删除（支持批量） */
  delete: (ids: number | number[]) => {
    const idArr = Array.isArray(ids) ? ids : [ids]
    return request.delete(`/system/print-template/${idArr.join(',')}`)
  }
}

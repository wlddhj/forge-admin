import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

// 文档响应
export interface DocumentResponse {
  id: number
  fileName: string
  fileSize: number
  fileType: string
  fileUrl: string
  content: string | null  // 新增
  status: number // 0: 处理中, 1: 已完成, 2: 处理失败
  summary: string | null
  modelName: string | null
  errorMessage: string | null
  createTime: string
  updateTime: string
}

// 文档查询参数
export interface DocumentQuery {
  fileName?: string
  fileType?: string
  status?: number
  createTimeStart?: string
  createTimeEnd?: string
  pageNum: number
  pageSize: number
}

// 文档上传响应
export interface DocumentUploadResponse {
  id: number
  fileName: string
  fileSize: number
  fileType: string
  status: number
}

export const documentApi = {
  // 上传文档
  upload: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<DocumentUploadResponse>('/ai/document/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 获取文档列表
  list: (params: DocumentQuery) =>
    request.get<PageResult<DocumentResponse>>('/ai/document/list', { params }),

  // 获取文档详情
  get: (id: number) =>
    request.get<DocumentResponse>(`/ai/document/${id}`),

  // 获取文档摘要 - 改为 GET 请求，使用默认模型
  summary: (id: number) =>
    request.get<DocumentResponse>(`/ai/document/${id}/summary`),

  // 删除文档
  delete: (id: number) =>
    request.delete(`/ai/document/${id}`),

  // 批量删除文档
  batchDelete: (ids: number[]) =>
    request.delete('/ai/document/batch', { data: ids })
}

// 导出独立函数
export const uploadDocument = (file: File) =>
  documentApi.upload(file).then(res => res.data)
export const getDocumentList = (params: DocumentQuery) =>
  documentApi.list(params).then(res => res.data)
export const getDocumentDetail = (id: number) =>
  documentApi.get(id).then(res => res.data)
export const getDocumentSummary = (id: number) =>
  documentApi.summary(id).then(res => res.data)
export const deleteDocument = (id: number) => documentApi.delete(id)
export const batchDeleteDocuments = (ids: number[]) => documentApi.batchDelete(ids)
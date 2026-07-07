import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

// ===== 类型（与后端 DTO 字段一致） =====

export interface ScreenListQuery {
  pageNum: number
  pageSize: number
  name?: string
  status?: 0 | 1
}

export interface ScreenDetailResponse {
  id: number
  code: string
  name: string
  description?: string
  /** 后端存的 JSON 字符串，前端用前需 JSON.parse */
  config: string
  configDraft: string
  theme: string
  status: 0 | 1
  version: number
  createTime: string
  updateTime: string
  createBy?: number
  remark?: string
}

export interface ScreenCreateRequest {
  id?: number
  code: string
  name: string
  description?: string
  theme?: string
  remark?: string
}

export interface ScreenCopyRequest {
  newCode: string
  newName: string
}

export interface ScreenDataSource {
  id?: number
  code: string
  name: string
  type: 'HTTP' | 'SQL'
  /** JSON 字符串：HTTP→{method,url,headers,params,timeout}；SQL→{sqlTemplate,paramSchema,maxRows} */
  config: string
  cacheSeconds?: number
  enabled?: 0 | 1
  remark?: string
}

export interface DataSourceListQuery {
  pageNum: number
  pageSize: number
  name?: string
  type?: 'HTTP' | 'SQL'
  enabled?: 0 | 1
}

export interface DataSourceExecuteRequest {
  params?: Record<string, unknown>
}

export interface DataSourceExecuteResponse {
  data: unknown
  fromCache: boolean
  executedAt: string
}

// ===== 大屏 API =====

export const screenApi = {
  page: (params: ScreenListQuery) =>
    request.get<PageResult<ScreenDetailResponse>>('/screen/list', { params }),
  get: (id: number) =>
    request.get<ScreenDetailResponse>(`/screen/${id}`),
  getByCode: (code: string) =>
    request.get<ScreenDetailResponse>(`/screen/code/${code}`),
  create: (data: ScreenCreateRequest) =>
    request.post<number>('/screen', data),
  update: (data: ScreenCreateRequest) =>
    request.put<void>('/screen', data),
  remove: (ids: number[]) =>
    request.delete<void>('/screen', { data: ids }),
  publish: (code: string) =>
    request.put<void>(`/screen/publish/${code}`),
  copy: (code: string, data: ScreenCopyRequest) =>
    request.post<number>(`/screen/copy/${code}`, data)
}

export const getScreenList = (params: ScreenListQuery) => screenApi.page(params).then(r => r.data)
export const getScreenDetail = (id: number) => screenApi.get(id).then(r => r.data)
export const getScreenByCode = (code: string) => screenApi.getByCode(code).then(r => r.data)
export const createScreen = (data: ScreenCreateRequest) => screenApi.create(data).then(r => r.data)
export const updateScreen = (data: ScreenCreateRequest) => screenApi.update(data).then(r => r.data)
export const deleteScreen = (ids: number[]) => screenApi.remove(ids).then(r => r.data)
export const publishScreen = (code: string) => screenApi.publish(code).then(r => r.data)
export const copyScreen = (code: string, data: ScreenCopyRequest) => screenApi.copy(code, data).then(r => r.data)

// ===== 数据源 API =====

export const dataSourceApi = {
  page: (params: DataSourceListQuery) =>
    request.get<PageResult<ScreenDataSource>>('/screen/data-source/list', { params }),
  get: (id: number) =>
    request.get<ScreenDataSource>(`/screen/data-source/${id}`),
  create: (data: ScreenDataSource) =>
    request.post<number>('/screen/data-source', data),
  update: (data: ScreenDataSource) =>
    request.put<void>('/screen/data-source', data),
  remove: (ids: number[]) =>
    request.delete<void>('/screen/data-source', { data: ids }),
  execute: (id: number, data: DataSourceExecuteRequest) =>
    request.post<DataSourceExecuteResponse>(`/screen/data-source/execute/${id}`, data)
}

export const getDataSourceList = (params: DataSourceListQuery) => dataSourceApi.page(params).then(r => r.data)
export const getDataSourceDetail = (id: number) => dataSourceApi.get(id).then(r => r.data)
export const createDataSource = (data: ScreenDataSource) => dataSourceApi.create(data).then(r => r.data)
export const updateDataSource = (data: ScreenDataSource) => dataSourceApi.update(data).then(r => r.data)
export const deleteDataSource = (ids: number[]) => dataSourceApi.remove(ids).then(r => r.data)
export const executeDataSource = (id: number, data: DataSourceExecuteRequest) =>
  dataSourceApi.execute(id, data).then(r => r.data)
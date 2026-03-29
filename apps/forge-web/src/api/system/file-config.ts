import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface FileConfig {
  id: number
  configName: string
  storageType: string
  endpoint: string
  bucketName: string
  accessKey: string
  secretKey: string
  domain: string
  basePath: string
  isDefault: number
  status: number
  remark: string
  createTime: string
}

export interface FileConfigQuery {
  pageNum: number
  pageSize: number
  configName?: string
  storageType?: string
  status?: number
}

export interface FileConfigPageResult {
  list: FileConfig[]
  total: number
}

// API 对象
export const fileConfigApi = {
  // 获取配置列表
  page: (params: FileConfigQuery) => {
    return request.get<PageResult<FileConfigPageResult>>('/system/file-config/list', { params })
  },

  // 获取配置详情
  get: (id: number) => {
    return request.get<FileConfig>(`/system/file-config/${id}`)
  },

  // 新增配置
  add: (data: Partial<FileConfig>) => {
    return request.post('/system/file-config', data)
  },

  // 更新配置
  update: (data: Partial<FileConfig>) => {
    return request.put('/system/file-config', data)
  },

  // 删除配置
  delete: (ids: number[]) => {
    return request.delete('/system/file-config', { data: ids })
  },

  // 设置默认配置
  setDefault: (id: number) => {
    return request.put(`/system/file-config/${id}/default`)
  },

  // 更新状态
  updateStatus: (id: number, status: number) => {
    return request.put(`/system/file-config/${id}/status`, null, { params: { status } })
  },

  // 测试连接
  test: (id: number) => {
    return request.put(`/system/file-config/${id}/conn`)
  }
}

// 导出独立函数供组件使用
export const getFileConfigList = (params: FileConfigQuery) => fileConfigApi.page(params)
export const addFileConfig = (data: Partial<FileConfig>) => fileConfigApi.add(data)
export const updateFileConfig = (data: Partial<FileConfig>) => fileConfigApi.update(data)
export const deleteFileConfig = (ids: number[]) => fileConfigApi.delete(ids)
export const setDefaultConfig = (id: number) => fileConfigApi.setDefault(id)
export const updateFileConfigStatus = (id: number, status: number) => fileConfigApi.updateStatus(id, status)
export const testFileConfig = (id: number) => fileConfigApi.test(id)

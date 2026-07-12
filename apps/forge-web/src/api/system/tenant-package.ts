import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

/**
 * 租户套餐响应实体
 */
export interface TenantPackageEntity {
  id: number
  name: string
  code: string
  status: number
  remark: string | null
  menuIds: number[]
  createTime: string
}

/**
 * 套餐查询参数
 */
export interface TenantPackageQuery {
  pageNum: number
  pageSize: number
  name?: string
  code?: string
  status?: number
}

/**
 * 套餐新增/修改请求
 */
export interface TenantPackageRequest {
  id?: number
  name: string
  code: string
  status?: number
  remark?: string
  menuIds?: number[]
}

// API 对象
export const tenantPackageApi = {
  // 分页查询套餐
  page: (params: TenantPackageQuery) =>
    request.get<PageResult<TenantPackageEntity>>('/system/tenant-package/list', { params }),

  // 获取套餐详情
  get: (id: number) =>
    request.get<TenantPackageEntity>(`/system/tenant-package/${id}`),

  // 获取所有启用的套餐（下拉选择用）
  enabled: () =>
    request.get<TenantPackageEntity[]>('/system/tenant-package/enabled').then(res => res.data),

  // 新增套餐
  add: (data: TenantPackageRequest) => request.post('/system/tenant-package', data),

  // 更新套餐
  update: (data: TenantPackageRequest) => request.put('/system/tenant-package', data),

  // 删除套餐（支持批量）
  delete: (ids: number[]) => request.delete(`/system/tenant-package/${ids.join(',')}`)
}

// 导出独立函数供组件使用
export const getTenantPackageList = (params: TenantPackageQuery) =>
  tenantPackageApi.page(params).then(res => res.data)
export const getTenantPackageDetail = (id: number) =>
  tenantPackageApi.get(id).then(res => res.data)
export const getEnabledTenantPackages = () => tenantPackageApi.enabled()
export const addTenantPackage = (data: TenantPackageRequest) =>
  tenantPackageApi.add(data)
export const updateTenantPackage = (data: TenantPackageRequest) =>
  tenantPackageApi.update(data)
export const deleteTenantPackage = (ids: number[]) => tenantPackageApi.delete(ids)

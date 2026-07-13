import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

/**
 * 租户响应实体
 */
export interface TenantEntity {
  id: number
  name: string
  code: string
  contactName: string
  contactPhone: string
  status: number
  packageId: number | null
  packageName: string | null
  expireTime: string | null
  website: string | null
  remark: string | null
  createTime: string
  /**
   * 初始管理员密码（仅 addTenant 首次响应返回，前端必须展示给用户保存）
   */
  initialAdminPassword?: string
}

/**
 * 租户查询参数
 */
export interface TenantQuery {
  pageNum: number
  pageSize: number
  name?: string
  code?: string
  status?: number
  packageId?: number
}

/**
 * 租户新增/修改请求
 */
export interface TenantRequest {
  id?: number
  name: string
  code: string
  contactName?: string
  contactPhone?: string
  status?: number
  packageId?: number | null
  expireTime?: string | null
  website?: string
  remark?: string
  /** 租户管理员用户名（不传则默认 admin） */
  adminUsername?: string
}

// API 对象
export const tenantApi = {
  // 分页查询租户
  page: (params: TenantQuery) =>
    request.get<PageResult<TenantEntity>>('/system/tenant/list', { params }),

  // 获取租户详情
  get: (id: number) => request.get<TenantEntity>(`/system/tenant/${id}`),

  // 新增租户
  add: (data: TenantRequest) =>
    request.post<TenantEntity>('/system/tenant', data).then(res => res.data),

  // 更新租户
  update: (data: TenantRequest) => request.put('/system/tenant', data),

  // 删除租户（支持批量）
  delete: (ids: number[]) => request.delete(`/system/tenant/${ids.join(',')}`),

  // 更新租户状态
  changeStatus: (id: number, status: number) =>
    request.put(`/system/tenant/${id}/status`, null, { params: { status } })
}

// 导出独立函数供组件使用
export const getTenantList = (params: TenantQuery) =>
  tenantApi.page(params).then(res => res.data)
export const getTenantDetail = (id: number) =>
  tenantApi.get(id).then(res => res.data)
export const addTenant = (data: TenantRequest) => tenantApi.add(data)
export const updateTenant = (data: TenantRequest) => tenantApi.update(data)
export const deleteTenant = (ids: number[]) => tenantApi.delete(ids)
export const changeTenantStatus = (id: number, status: number) =>
  tenantApi.changeStatus(id, status)

import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface AppUserEntity {
  id: number
  nickname: string
  avatar: string
  phone: string
  phoneVerified: number
  openId: string
  status: number
  lastLoginTime: string
  deactivatedTime: string | null
  createTime: string
  unionId?: string
  updateTime?: string
}

export interface AppUserQuery {
  nickname?: string
  phone?: string
  openId?: string
  status?: number
  createTimeStart?: string
  createTimeEnd?: string
  pageNum: number
  pageSize: number
}

export const appUserApi = {
  list: (params: AppUserQuery) =>
    request.get<PageResult<AppUserEntity>>('/system/app-user/list', { params }),
  detail: (id: number) =>
    request.get<AppUserEntity>(`/system/app-user/${id}`),
  updateStatus: (id: number, status: number) =>
    request.put(`/system/app-user/${id}/status`, { status }),
  resetProfile: (id: number, data: { nickname?: string; avatar?: string }) =>
    request.put(`/system/app-user/${id}/profile`, data),
  delete: (id: number) =>
    request.delete(`/system/app-user/${id}`)
}

// 导出独立函数供组件使用
export const getAppUserList = (params: AppUserQuery) => appUserApi.list(params).then(res => res.data)
export const getAppUserDetail = (id: number) => appUserApi.detail(id).then(res => res.data)
export const updateAppUserStatus = (id: number, status: number) => appUserApi.updateStatus(id, status)
export const resetAppUserProfile = (id: number, data: { nickname?: string; avatar?: string }) => appUserApi.resetProfile(id, data)
export const deleteAppUser = (id: number) => appUserApi.delete(id)
import request from '@/utils/request'

export interface ImChannelConfig {
  id: number
  tenantId: number
  channel: 'wework' | 'dingtalk'
  enabled: number
  corpId?: string
  appKey?: string
  agentId: string
  hasSecret: boolean
  remark?: string
  updateTime?: string
}

export interface ImChannelConfigRequest {
  tenantId: number
  channel: string
  enabled: number
  corpId?: string
  appKey?: string
  agentId: string
  /** 空串/不传 = 保留原 secret */
  secret?: string
  remark?: string
}

export interface TenantOption {
  id: number
  name: string
  status?: number
}

export const imConfigApi = {
  listByTenant: (tenantId: number) =>
    request.get<ImChannelConfig[]>('/workflow/im-config', { params: { tenantId } }).then(res => res.data),
  save: (data: ImChannelConfigRequest) =>
    request.put('/workflow/im-config', data),
  remove: (id: number) =>
    request.delete(`/workflow/im-config/${id}`),
  /** 租户下拉（需 system:tenant:list 权限；非租户模式选默认租户 1） */
  tenantList: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<{ list: TenantOption[]; total: number }>('/system/tenant/list', { params }).then(res => res.data)
}

import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

/** 插件注册实体（sys_plugin） */
export interface PluginEntity {
  id: number
  pluginCode: string
  name: string
  description: string | null
  provider: string | null
  runningVersion: string
  globalStatus: number
  menusSynced: number
  updateTime: string | null
}

/** 租户插件状态（授权抽屉行数据） */
export interface TenantPluginVO {
  tenantId: number
  tenantName: string | null
  enabled: number
  disabledReason: string | null
  enabledTime: string | null
  disabledTime: string | null
  licenseKey: string | null
  licensee: string | null
  notAfter: string | null
  expiringSoon: boolean
  effectiveScopes: string[] | null
}

/** License 记录 */
export interface PluginLicenseEntity {
  id: number
  licenseKey: string
  tenantId: number
  tenantCode: string
  licenseeName: string | null
  pluginCode: string
  scopes: string | null
  notBefore: string
  notAfter: string
  issuedAt: string | null
  status: number
  verifyState: number
  failReason: string | null
  createTime: string | null
}

/** scope 声明与授予态（勾选式数据源） */
export interface PluginScopeVO {
  scope: string
  name: string
  description: string | null
  granted: boolean
  grantedByDefault: boolean
  source: 'default' | 'admin' | 'license' | 'none'
}

// API 对象
export const pluginApi = {
  // 插件分页
  page: (params: { pageNum: number; pageSize: number; keyword?: string }) =>
    request.get<PageResult<PluginEntity>>('/plugin/page', { params }),

  // 全部注册插件
  list: () => request.get<PluginEntity[]>('/plugin/list'),

  // 插件租户明细（启停/授权/到期）
  tenants: (pluginCode: string) =>
    request.get<TenantPluginVO[]>(`/plugin/${pluginCode}/tenants`),

  // 启用（未授权不可启用）
  enable: (pluginCode: string, tenantId: number) =>
    request.post(`/plugin/${pluginCode}/tenants/${tenantId}/enable`),

  // 停用（菜单隐藏、数据保留）
  disable: (pluginCode: string, tenantId: number) =>
    request.post(`/plugin/${pluginCode}/tenants/${tenantId}/disable`),

  // 平台总闸
  updateGlobalStatus: (pluginCode: string, globalStatus: number) =>
    request.put(`/plugin/${pluginCode}/global-status`, null, { params: { globalStatus } }),

  // License 导入（一码多插件原子导入）
  importLicense: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<{ imported: number }>('/plugin/license/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // License 分页
  licensePage: (params: {
    pageNum: number
    pageSize: number
    tenantId?: number
    pluginCode?: string
    status?: number
  }) => request.get<PageResult<PluginLicenseEntity>>('/plugin/license/page', { params }),

  // 撤销 License（联动停用）
  revokeLicense: (id: number) => request.post(`/plugin/license/${id}/revoke`),

  // 签发表单元数据（能力开关+租户+插件声明集）
  issueFormData: () =>
    request.get<{
      enabled: boolean
      tenants: Array<{ id: number; name: string; code: string }>
      plugins: Array<{
        pluginCode: string
        name: string
        declaredScopes: Array<{ scope: string; name: string; description: string; grantedByDefault: boolean }>
      }>
    }>('/plugin/license/issue/form-data'),

  // 在线签发（activate=true 签发并落库生效）
  issueLicense: (data: {
    tenantId: number
    pluginCodes: string[]
    licensee: string
    notBefore?: string
    notAfter: string
    scopes?: string[]
    licenseKey?: string
    activate: boolean
  }) =>
    request.post<{ licenseKey: string; activated: boolean; licenseContent: string }>(
      '/plugin/license/issue',
      data
    ),

  // scope 声明集与授予态
  scopeList: (tenantId: number, pluginCode: string) =>
    request.get<PluginScopeVO[]>('/plugin/scope/list', { params: { tenantId, pluginCode } }),

  // 授予/回收 scope（勾选切换）
  grantScope: (tenantId: number, pluginCode: string, scope: string) =>
    request.post('/plugin/scope/grant', null, { params: { tenantId, pluginCode, scope } }),

  revokeScope: (tenantId: number, pluginCode: string, scope: string) =>
    request.post('/plugin/scope/revoke', null, { params: { tenantId, pluginCode, scope } })
}

import request from '@/utils/request'
import type { OAuth2Client, ClientCreateRequest, ClientUpdateRequest, ClientQueryRequest } from '@/types/oauth2-client'

export const oauth2ClientApi = {
  /** 查询客户端列表 */
  list: (params: ClientQueryRequest) => request.get<OAuth2Client[]>('/system/oauth2-client/list', { params }),

  /** 查询客户端详情 */
  get: (id: string) => request.get<OAuth2Client>(`/system/oauth2-client/${id}`),

  /** 新增客户端 */
  add: (data: ClientCreateRequest) => request.post<Record<string, string>>('/system/oauth2-client', data),

  /** 修改客户端 */
  update: (data: ClientUpdateRequest) => request.put('/system/oauth2-client', data),

  /** 删除客户端 */
  delete: (ids: string[]) => request.delete('/system/oauth2-client', { data: ids }),

  /** 重新生成密钥 */
  regenerateSecret: (id: string) => request.put<Record<string, string>>(`/system/oauth2-client/${id}/secret`),
}

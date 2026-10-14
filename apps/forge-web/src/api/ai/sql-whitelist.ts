import request from '@/utils/request'

/** SQL 白名单条目 */
export interface SqlWhitelistItem {
  id?: number
  tableName: string
  tableComment?: string
  enabled: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface SqlWhitelistQuery {
  pageNum: number
  pageSize: number
  tableName?: string
  enabled?: number
}

export const sqlWhitelistApi = {
  page: (params: SqlWhitelistQuery) =>
    request.get<{ list: SqlWhitelistItem[]; total: number }>('/ai/sql-whitelist/list', { params }),
  add: (data: Partial<SqlWhitelistItem>) => request.post('/ai/sql-whitelist', data),
  update: (data: Partial<SqlWhitelistItem>) => request.put('/ai/sql-whitelist', data),
  remove: (ids: number[]) => request.delete('/ai/sql-whitelist', { data: ids })
}

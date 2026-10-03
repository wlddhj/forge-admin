/**
 * forge-admin 数据源 API 封装。
 */
import request from '@/api/axios'

export interface DataSourceExecuteRequest {
  params?: Record<string, unknown>
}

export interface DataSourceExecuteResponse {
  data: unknown
  fromCache: boolean
  executedAt: string
}

export const executeDataSource = (
  id: number,
  data: DataSourceExecuteRequest
): Promise<DataSourceExecuteResponse> =>
  request.post(`/screen/data-source/execute/${id}`, data)

export interface DataSourceListItem {
  id: number
  code: string
  name: string
  type: string
  enabled: number
}

interface DataSourceListResult {
  list: DataSourceListItem[]
  total: number
}

export const listDataSources = (pageNum = 1, pageSize = 100): Promise<DataSourceListResult> =>
  request.get('/screen/data-source/list', { params: { pageNum, pageSize } })

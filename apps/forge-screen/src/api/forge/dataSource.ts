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

import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/** 用量日聚合行 */
export interface ApiUsageRow {
  id: number
  userId: number
  modelId: number
  usageDate: string
  requestCount: number
  successCount: number
  failureCount: number
  totalInputTokens: number
  totalOutputTokens: number
  avgResponseTime: number
}

/** 用量汇总 */
export interface ApiUsageSummary {
  totalRequest: number
  successCount: number
  failureCount: number
  totalTokens: number
  avgResponseMs: number
}

export interface UsageQuery {
  pageNum: number
  pageSize: number
  userId?: number
  modelId?: number
  startDate?: string
  endDate?: string
}

export const usageApi = {
  page: (params: UsageQuery) =>
    request.get<PageResult<ApiUsageRow>>('/ai/usage/page', { params }).then(res => res.data),
  summary: (params: Omit<UsageQuery, 'pageNum' | 'pageSize'>) =>
    request.get<ApiUsageSummary>('/ai/usage/summary', { params }).then(res => res.data)
}

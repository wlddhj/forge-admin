import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface ProcessInstanceCopy {
  id: number
  startUserId: number
  startUserName: string
  processInstanceName: string
  processInstanceId: string
  category: string
  activityId: string
  activityName: string
  taskId: string
  userId: number
  userName: string
  reason: string
  createTime: string
}

export interface CopyQuery {
  pageNum: number
  pageSize: number
  processInstanceName?: string
  userId?: number
}

export const copyApi = {
  page: (params: CopyQuery) =>
    request.get<PageResult<ProcessInstanceCopy>>('/workflow/copy/list', { params }).then(res => res.data),
}

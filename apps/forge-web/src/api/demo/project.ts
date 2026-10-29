import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ProjectItem, ProjectSubmitRequest } from '@/types/project'

/**
 * 项目立项 API（业务示例模块，接入工作流示例）
 */
export const projectApi = {
  page: (params: { pageNum: number; pageSize: number; projectName?: string; approveStatus?: string }) =>
    request.get<PageResult<ProjectItem>>('/demo/project/list', { params }).then(res => res.data),
  detail: (id: string) =>
    request.get<ProjectItem>(`/demo/project/${id}`).then(res => res.data),
  submit: (data: ProjectSubmitRequest) =>
    request.post<string>('/demo/project/submit', data).then(res => res.data),
  cancel: (id: string) =>
    request.post(`/demo/project/${id}/cancel`)
}

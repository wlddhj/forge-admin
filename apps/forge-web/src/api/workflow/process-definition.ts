import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ProcessDefinition, ProcessDefinitionQuery, ProcessDeployRequest } from '@/types/workflow'

export const processDefinitionApi = {
  page: (params: ProcessDefinitionQuery) =>
    request.get<PageResult<ProcessDefinition>>('/workflow/process-definition/list', { params }).then(res => res.data),
  getById: (id: string) =>
    request.get<ProcessDefinition>(`/workflow/process-definition/${id}`).then(res => res.data),
  deploy: (data: ProcessDeployRequest) =>
    request.post('/workflow/process-definition/deploy', data),
  getXml: (id: string) =>
    request.get<string>(`/workflow/process-definition/${id}/xml`).then(res => res.data),
  getDiagram: (id: string) =>
    request.get(`/workflow/process-definition/${id}/diagram`, { responseType: 'blob' }),
suspend: (id: string) =>
    request.put(`/workflow/process-definition/${id}/suspend`),
  activate: (id: string) =>
    request.put(`/workflow/process-definition/${id}/activate`),
  delete: (deploymentId: string) =>
    request.delete(`/workflow/process-definition/${deploymentId}`)
}

import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { FieldDef, PaperMarginCode, PaperOrientationCode, PaperSizeCode, TemplateBrief, TemplateForm, TemplateItem, TemplateVersionItem } from '@/types/contract'

/**
 * 合同模板 API
 */
export const templateApi = {
  page: (params: {
    pageNum: number
    pageSize: number
    name?: string
    categoryId?: string
    status?: number
  }) => request.get<PageResult<TemplateItem>>('/contract/template/list', { params }).then(res => res.data),

  listEnabled: () =>
    request.get<TemplateBrief[]>('/contract/template/list-enabled').then(res => res.data),

  create: (data: TemplateForm) =>
    request.post<string>('/contract/template', data).then(res => res.data),

  update: (data: TemplateForm & { id: string }) =>
    request.put('/contract/template', data),

  /** 仅更新纸张配置（编辑器内调整） */
  updatePaper: (data: { id: string; paperSize: PaperSizeCode; customWidthMm?: number; customHeightMm?: number; paperOrientation?: PaperOrientationCode; marginPreset?: PaperMarginCode }) =>
    request.put('/contract/template/paper', data),

  changeStatus: (id: string, status: number) =>
    request.put('/contract/template/status', { id, status }),

  detail: (id: string) =>
    request.get<TemplateItem>(`/contract/template/${id}`).then(res => res.data),

  pageVersions: (id: string, params: { pageNum: number; pageSize: number }) =>
    request.get<PageResult<TemplateVersionItem>>(`/contract/template/${id}/versions`, { params })
      .then(res => res.data),

  getVersion: (versionId: string) =>
    request.get<TemplateVersionItem>(`/contract/template/version/${versionId}`).then(res => res.data),

  saveVersion: (templateId: string, data: { contentHtml: string; fieldDefs: FieldDef[]; changeLog?: string }) =>
    request.post<string>(`/contract/template/${templateId}/version`, data).then(res => res.data),

  updateFieldDefs: (versionId: string, fieldDefs: FieldDef[]) =>
    request.put(`/contract/template/version/${versionId}/field-defs`, { fieldDefs }),

  setCurrentVersion: (versionId: string) =>
    request.put(`/contract/template/version/${versionId}/set-current`),

  remove: (id: string) => request.delete(`/contract/template/${id}`)
}

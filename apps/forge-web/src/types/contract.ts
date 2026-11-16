/**
 * 合同模块类型定义
 */

/** 变量字段定义 */
export interface FieldDef {
  key: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'date' | 'select'
  required?: boolean
  options?: string[]
}

/** 纸张规格 */
export type PaperSizeCode = 'A4' | 'A5' | 'LETTER' | 'LEGAL' | 'CUSTOM'

/** 模板基础信息 */
export interface TemplateItem {
  id: string
  categoryCode: string
  name: string
  currentVersionId?: string | null
  currentVersionNo?: number | null
  status: number
  paperSize: PaperSizeCode
  customWidthMm?: number | null
  customHeightMm?: number | null
  remark?: string
  createBy?: string
  createTime?: number
  updateTime?: number
}

/** 启用模板下拉项 */
export interface TemplateBrief {
  id: string
  categoryCode: string
  name: string
  currentVersionId: string
  paperSize: PaperSizeCode
  customWidthMm?: number | null
  customHeightMm?: number | null
}

/** 模板版本 */
export interface TemplateVersionItem {
  id: string
  templateId: string
  versionNo: number
  contentHtml?: string
  fieldDefs?: FieldDef[]
  changeLog?: string
  createBy?: string
  createTime?: number
}

/** 模板基础信息表单 */
export interface TemplateForm {
  id?: string
  categoryCode: string
  name: string
  paperSize: PaperSizeCode
  customWidthMm?: number
  customHeightMm?: number
  remark?: string
}

/** 合同记录 */
export interface ContractItem {
  id: string
  contractNo?: string | null
  title: string
  templateId: string
  templateName?: string
  categoryCode?: string
  templateVersionId: string
  templateVersionNo?: number
  fieldValues?: Record<string, unknown>
  status: number
  pdfAttachmentId?: string | null
  /** 定稿 PDF 直链（详情用） */
  pdfFileUrl?: string | null
  processInstanceId?: string | null
  processNo?: string | null
  createBy?: string
  createTime?: number
  updateTime?: number
}

/** el-tag type 配色 */
export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/** 合同状态标签配色 */
export const CONTRACT_STATUS_TAG: Record<number, TagType> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger',
  4: 'info'
}

/** 合同状态文案 */
export const CONTRACT_STATUS_LABEL: Record<number, string> = {
  0: '草稿',
  1: '审批中',
  2: '已通过',
  3: '已驳回',
  4: '已撤销'
}

/** 可编辑态（草稿/驳回/撤销） */
export const isEditableStatus = (status: number): boolean => status === 0 || status === 3 || status === 4

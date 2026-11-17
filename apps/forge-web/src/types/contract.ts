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
export type PaperSizeCode = 'A4' | 'A5' | 'B3' | 'LETTER' | 'LEGAL' | 'CUSTOM'

/** 纸张方向 */
export type PaperOrientationCode = 'PORTRAIT' | 'LANDSCAPE'

/** 页边距档位（Word 同口径） */
export type PaperMarginCode = 'NARROW' | 'NORMAL' | 'WIDE'

/** 合同分类树节点 */
export interface CategoryNode {
  id: string
  categoryName: string
  categoryCode: string
  parentId: string
  sortOrder: number
  status: number
  remark?: string
  /** 是否叶子（仅启用叶子可挂模板） */
  leaf: boolean
  /** 前端标注：不可选（非叶子或停用） */
  disabled?: boolean
  children?: CategoryNode[]
}

/** 模板基础信息 */
export interface TemplateItem {
  id: string
  categoryId: string
  categoryName?: string
  categoryCode?: string
  name: string
  currentVersionId?: string | null
  currentVersionNo?: number | null
  status: number
  paperSize: PaperSizeCode
  paperOrientation?: PaperOrientationCode
  marginPreset?: PaperMarginCode
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
  categoryId: string
  categoryName?: string
  categoryCode?: string
  name: string
  currentVersionId: string
  paperSize: PaperSizeCode
  paperOrientation?: PaperOrientationCode
  marginPreset?: PaperMarginCode
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
  categoryId: string | undefined
  name: string
  paperSize: PaperSizeCode
  paperOrientation?: PaperOrientationCode
  marginPreset?: PaperMarginCode
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
  categoryName?: string
  pdfAttachmentId?: string | null
  /** 定稿 PDF 直链（详情用） */
  pdfFileUrl?: string | null
  processInstanceId?: string | null
  processNo?: string | null
  /** 备注（管理性信息，任意状态可改） */
  remark?: string | null
  createBy?: string
  createTime?: number
  updateTime?: number
}

/** el-tag type 配色（模板方向列等处使用）；合同状态文案与配色走 contract_status 字典（constants/dict.ts） */
export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/** 可编辑态（草稿/驳回/撤销） */
export const isEditableStatus = (status: number): boolean => status === 0 || status === 3 || status === 4

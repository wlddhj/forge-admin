/**
 * 项目立项（业务模块接入工作流示例）
 */
export interface ProjectItem {
  /** 业务ID（全局 Jackson 将 Long 序列化为字符串） */
  id: string
  projectName: string
  totalAmount?: number
  bizUser?: string
  deptName?: string
  remark?: string
  processInstanceId?: string
  approveStatus: 'APPROVING' | 'APPROVED' | 'REJECTED' | 'CANCELED'
  approveStatusName: string
  createBy?: string
  createTime?: string
  cancelable?: boolean
}

export interface ProjectSubmitRequest {
  projectName: string
  totalAmount?: number
  bizUser?: string
  deptName?: string
  remark?: string
}

/** 审批状态 → 标签颜色 */
export const PROJECT_STATUS_TAG: Record<string, 'warning' | 'success' | 'danger' | 'info'> = {
  APPROVING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELED: 'info'
}

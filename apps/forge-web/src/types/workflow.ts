// 流程分类
export interface WfCategory {
  id: number
  categoryName: string
  categoryCode: string
  parentId: number
  sortOrder: number
  status: number
  remark: string
  createTime: string
  children?: WfCategory[]
}

export interface CategoryQuery {
  pageNum: number
  pageSize: number
  categoryName?: string
  categoryCode?: string
  status?: number
}

export interface CategoryRequest {
  id?: number
  categoryName: string
  categoryCode: string
  parentId?: number
  sortOrder?: number
  status?: number
  remark?: string
}

// 流程定义
export interface ProcessDefinition {
  id: string
  key: string
  name: string
  version: number
  categoryId: number | null
  categoryName: string
  deploymentId: string
  suspensionState: number  // 1=active, 2=suspended
  description: string
  formKey: string
  resourceName: string
  diagramResourceName: string
  createTime: string
  deployUserName: string
  bpmnXml: string
}

export interface ProcessDefinitionQuery {
  pageNum: number
  pageSize: number
  name?: string
  key?: string
  categoryId?: number
  suspensionState?: number
}

export interface ProcessDeployRequest {
  name: string
  key: string
  categoryId?: number
  description?: string
  bpmnXml: string
}

// 流程实例
export interface ProcessInstance {
  id: string
  processDefinitionId: string
  processDefinitionName: string
  processDefinitionKey: string
  businessKey: string
  startTime: string
  endTime: string | null
  durationInMillis: number
  startUserId: string
  startUserName: string
  currentActivityName: string
  suspensionState: number
  deleteReason: string | null
}

export interface ProcessInstanceQuery {
  pageNum: number
  pageSize: number
  processDefinitionName?: string
  startUserName?: string
  startTimeBegin?: string
  startTimeEnd?: string
  status?: string  // running, finished, terminated
}

export interface ProcessStartRequest {
  processDefinitionId: string
  businessKey?: string
  variables?: Record<string, any>
  comment?: string
}

// 任务
export interface TaskInfo {
  id: string
  name: string
  taskDefinitionKey: string
  processInstanceId: string
  processDefinitionName: string
  assignee: string
  assigneeName: string
  owner: string
  ownerName: string
  createTime: string
  claimTime: string | null
  dueDate: string | null
  category: string
  variables: Record<string, any>
  endTime: string | null
}

export interface TaskQuery {
  pageNum: number
  pageSize: number
  name?: string
  processDefinitionName?: string
  createTimeBegin?: string
  createTimeEnd?: string
}

export interface TaskApproveRequest {
  comment: string
  variables?: Record<string, any>
}

export interface TaskDelegateRequest {
  delegateUserId: number
  comment: string
}

export interface TaskTransferRequest {
  transferUserId: number
  comment: string
}

export interface TaskReturnRequest {
  targetTaskDefKey: string
  comment: string
}

// 审批意见
export interface ApprovalComment {
  id: number
  processInstanceId: string
  taskId: string
  taskDefKey: string
  taskName: string
  userId: number
  userName: string
  actionType: 'approve' | 'reject' | 'delegate' | 'transfer' | 'return' | 'claim' | 'submit' | 'cancel'
  commentText: string
  attachmentIds: string
  createTime: string
}

// 可退回节点
export interface ReturnNode {
  taskDefKey: string
  taskName: string
}

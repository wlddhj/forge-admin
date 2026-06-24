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
  formType: number | null
  formId: number | null
  autoCopyStrategy: number | null
  autoCopyParam: string | null
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
  formType?: number
  formId?: number
}

/** 用户任务节点信息（候选人策略） */
export interface UserTaskNode {
  taskDefKey: string
  taskName: string
  candidateStrategy: number
}

// 流程实例
export interface ProcessInstance {
  id: string
  processDefinitionId: string
  processDefinitionName: string
  processDefinitionKey: string
  processNo: string
  businessKey: string
  startTime: string
  endTime: string | null
  durationInMillis: number
  startUserId: string
  startUserName: string
  currentActivityName: string
  currentAssigneeNames?: string[]
  currentCandidateNames?: string[]
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
  /** 发起人自选审批人 key: taskDefKey, value: 审批人ID数组 */
  startUserSelectActors?: Record<string, string[]>
}

// 任务
export interface TaskInfo {
  id: string
  name: string
  taskDefinitionKey: string
  processInstanceId: string
  processDefinitionName: string
  processNo: string
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
  candidate?: boolean
  candidateUsers?: string[]
  actionType?: string
  commentText?: string
  nextActivityName?: string
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
  actionType: string
  commentText: string
  attachmentIds: string
  createTime: string
}

// 可退回节点
export interface ReturnNode {
  taskDefKey: string
  taskName: string
}

// 审批详情
export interface ApprovalDetail {
  processInstanceId: string
  processInstanceName: string
  processDefinitionId: string
  category: string
  status: number
  startTime: string
  endTime: string | null
  startUserId: number
  startUserName: string
  bpmnXml: string
  nodes: ApprovalNode[]
}

export interface ApprovalNode {
  activityId: string
  activityName: string
  activityType: string
  status: number  // 1=进行中 2=已完成
  startTime: string
  endTime: string | null
  tasks: ApprovalTask[]
  candidateUsers?: string[]
}

export interface ApprovalTask {
  taskId: string
  userId: number
  userName: string
  status: number  // 1=进行中 2=已完成
  comment: string
  createTime: string
  endTime: string | null
}

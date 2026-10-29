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
  categoryName?: string
  categoryCode?: string
  status?: number
}

export interface CategoryRequest {
  id?: number
  categoryName: string
  /** 自动生成（父编码+2位随机段），入参忽略 */
  categoryCode?: string
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
  /** 流程状态 0不可用 1可用 2历史版本 */
  processState?: number
  description: string
  formKey: string
  formType: number | null
  formId: number | null
  autoCopyStrategy: number | null
  autoCopyParam: string | null
  /** 发起范围 0全员 1指定人员 2均不可发起 */
  useScope: number | null
  /** 发起范围人员名单JSON（用户ID数组） */
  useScopeUsers: string | null
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
  deleteReason: string | null  /** 紧急程度 0 常规 1 重要不紧急 2 紧急不重要 3 紧急且重要 */
  urgent?: number
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
  /** 紧急程度 0 常规 1 重要不紧急 2 紧急不重要 3 紧急且重要 */
  urgent?: number
}

// 任务
export interface TaskInfo {
  id: string
  name: string
  taskDefinitionKey: string
  processInstanceId: string
  processDefinitionName: string
  processNo: string
  startUserId: string
  startUserName: string
  startTime: string
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
  /** 当前用户是否从候选组（角色/部门）签收（待办行展示“取消签收”入口） */
  claimedFromGroup?: boolean
  actionType?: string
  commentText?: string
  nextActivityName?: string
  withdrawn?: boolean
  /** 是否可拿回（已办任务：下一节点待人审批任务尚未办理） */
  canReclaim?: boolean
  /** 紧急程度码值（0-3，渲染走 wf_urgent 字典） */
  urgent?: number
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
  attachmentIds?: number[]
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
  attachments?: WorkflowCommentAttachment[]
  createTime: string
}

// 审批评论附件明细
export interface WorkflowCommentAttachment {
  id: number
  originalName: string
  fileUrl: string
}

// 实例 AI 决策记录（分支路由 + 审批决策）
export interface AiDecisionRecords {
  routings: AiRoutingDecision[]
  approvals: AiApprovalDecision[]
}

export interface AiRoutingDecision {
  gatewayNodeKey: string
  gatewayNodeName: string
  gatewayType: number
  gatewayTypeName: string
  selectedBranches: string[]
  selectedBranchNames: string[]
  confidence: number | null
  reasoning: string | null
  rawResponse: string | null
  status: string // SUCCESS / FALLBACK / LOW_CONFIDENCE / TIMEOUT / ERROR
  createTime: string
}

export interface AiApprovalDecision {
  taskId: number
  taskName: string
  decision: string // APPROVE / REJECT / MANUAL
  confidence: number | null
  reasoning: string | null
  status: string
  provider: string | null
  modelName: string | null
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
  status: number  // 0=未到达 1=进行中 2=已完成
  startTime: string | null
  endTime: string | null
  tasks: ApprovalTask[]
  candidateUsers?: string[]
}

export interface ApprovalTask {
  taskId: string
  userId: number
  userName: string
  status: number  // 1=进行中 2=已完成
  /** 审批动作（wf_action_type 字典值），未产生意见时为空 */
  actionType?: string
  comment: string
  createTime: string | null
  endTime: string | null
}

// 业务回调
export interface BusinessCallback {
  id: number
  name: string
  url: string
  secret: string | null
  events: string[]
  processKey: string | null
  includeVariables: number
  enabled: number
  timeoutMs: number
  createTime: string
  updateTime: string
}

export interface BusinessCallbackRecord {
  id: number
  callbackId: number
  callbackName: string
  eventType: string
  processKey: string | null
  businessKey: string | null
  instanceId: number | null
  taskId: number | null
  nodeKey: string | null
  payload: string | null
  httpStatus: number | null
  responseBody: string | null
  status: string
  retryCount: number
  errorMsg: string | null
  createTime: string
}

/**
 * flowlong-designer 数据类型定义
 *
 * 前端和后端使用一致的 TaskType：
 * - end(-1) = 结束节点
 * - major(0) = 主办/发起人节点
 * - approval(1) = 审批节点
 * - cc(2) = 抄送节点
 * - conditionNode(3) = 条件审批节点
 * - conditionBranch(4) = 条件分支节点
 * - callProcess(5) = 子流程
 * - timer(6) = 定时器
 * - trigger(7) = 触发器任务
 * - parallelBranch(8) = 并行分支
 * - inclusiveBranch(9) = 包容分支
 * - routeBranch(23) = 路由分支
 * - autoPass(30) / autoReject(31) = 自动通过/自动拒绝
 */

/**
 * AI 审批配置
 */
export interface FlowlongAiApprovalConfig {
  enabled: boolean // 是否启用 AI 审批
  provider: string // AI 模型提供商（deepseek/qwen/glm/ernie）
  modelName: string // 模型名称
  confidenceThreshold: number // 置信度阈值（0-100）
  fallbackStrategy: string // 回退策略（MANUAL/DEFAULT_PASS/DEFAULT_REJECT）
  customPrompt?: string // 自定义审批提示词
  timeoutSeconds?: number // 超时时间（秒）
}

/**
 * AI 智能路由配置（网关节点）
 */
export interface FlowlongAiRoutingConfig {
  prompt?: string // 路由规则提示词
  modelName?: string // 模型名称（留空走系统默认）
  timeoutSeconds?: number // 决策超时（秒，5-60）
  confidenceThreshold?: number // 置信度阈值（0-100）
}

// flowlong-designer 的节点类型（与 FlowLong TaskType 一致）
export interface FlowlongNodeModel {
  nodeName: string
  nodeKey: string
  type: number // -1=结束, 0=发起人, 1=审批, 2=抄送, 3=条件审批, 4=条件分支, 5=子流程, 6=定时器, 7=触发器, 8=并行分支, 9=包容分支, 23=路由分支, 30/31=自动通过/拒绝
  /** 后端扩展配置（提醒/AI审批/条件列表等，后端→设计器方向消费） */
  extendConfig?: Record<string, unknown>
  setType?: number
  nodeAssigneeList?: FlowlongNodeAssignee[]
  examineLevel?: number // 指定主管层级
  directorLevel?: number // 连续主管审批层级
  directorMode?: number // 连续主管审批方式
  selectMode?: number // 发起人自选类型
  termAuto?: boolean // 超时自动审批
  term?: number // 审批期限（小时）
  termMode?: number // 超时后执行类型 0=自动通过, 1=自动拒绝
  // 超时提醒配置
  remindAuto?: boolean // 是否启用超时提醒
  remindAdvanceMinutes?: number // 提前提醒时间（分钟）
  remindIntervalHours?: number // 提醒间隔（小时）
  remindChannels?: string[] // 提醒渠道 ['websocket', 'email']
  examineMode?: number // 多人审批方式 1=依次审批, 2=会签, 3=或签, 4=票签
  passWeight?: number // 票签通过权重门槛（默认50，权重合计100）
  userSelectFlag?: boolean // 允许发起人自选抄送人
  expression?: string // 表达式
  conditionNodes?: FlowlongConditionNode[]
  parallelNodes?: FlowlongConditionNode[] // 并行分支（type=8）
  inclusiveNodes?: FlowlongConditionNode[] // 包容分支（type=9）
  routeNodes?: FlowlongConditionNode[] // 路由分支（type=23，nodeKey 为 route:目标nodeKey）
  conditionList?: FlowlongCondition[][] // 条件列表（条件组数组；type=3 条件审批用）
  // 驳回策略（type=1/3；引擎顶层字段）
  rejectStrategy?: number // 1=回发起人 2=上一节点 3=指定节点 4=终止流程
  rejectStart?: number // 重审策略 1=从驳回节点继续 2=回上一节点
  rejectTargetKey?: string // 策略3目标节点（封装层语义，存 extendConfig）
  // 子流程（type=5）
  callProcess?: string // "processId:processName"
  callAsync?: boolean // 异步子流程（本轮仅透传）
  // 定时器（type=6，设计器态；保存时合成 extendConfig.time）
  timerMode?: 'duration' | 'clock' // 时长式 / 时刻式
  timerValue?: number // 时长数值
  timerUnit?: 'd' | 'h' | 'm' // 时长单位
  timerClock?: string // 时刻 HH:mm:ss
  // 触发器任务（type=7）配置 - 4 种业务模式共享
  triggerType?: string // expression / bean / class / delegateExpression
  triggerExpression?: string // expression / delegateExpression 模式用
  triggerBean?: string // bean 模式: Spring Bean 名
  triggerClass?: string // class 模式: FQCN
  triggerMethod?: string // bean/class/delegateExpression 模式: 方法名
  continueOnError?: boolean // 异常时是否继续流转
  childNode?: FlowlongNodeModel
  // AI 审批配置
  aiApproval?: boolean // 是否启用 AI 审批（开关）
  aiApprovalConfig?: FlowlongAiApprovalConfig // AI 审批详细配置
  // AI 智能路由（type=4/9/23 网关；启用时落 callAi 标记 + extendConfig.aiRouting）
  aiRouting?: boolean // 是否启用 AI 路由（开关）
  aiRoutingConfig?: FlowlongAiRoutingConfig // AI 路由详细配置
  callAi?: string // 引擎 AI 启用标记（后端透传字段，启用 AI 路由时为 '1'）
}

export interface FlowlongNodeAssignee {
  id: string
  name: string
  weight?: number // 票签权重（examineMode=4 时生效）
}

export interface FlowlongConditionNode {
  nodeName: string
  nodeKey: string
  type: number
  priorityLevel: number
  conditionMode?: number
  conditionList: FlowlongCondition[][]
  childNode?: FlowlongNodeModel
}

export interface FlowlongCondition {
  label: string
  field: string
  operator: string
  value: string
}

export interface FlowlongProcessModel {
  id?: number | string
  name: string
  key: string
  nodeConfig: FlowlongNodeModel
}

/** 定时器时长式 extendConfig.time 格式：数值:单位（d/h/m） */
const DURATION_TIME_RE = /^(\d+):([dhm])$/
/** 定时器时刻式 extendConfig.time 格式：HH:mm:ss */
const CLOCK_TIME_RE = /^(\d{2}):(\d{2}):(\d{2})$/

/**
 * 数据转换 Hook
 *
 * 前端和后端使用一致的节点类型值，无需转换
 */
export function useFlowLongDataTransform() {

  /**
   * 分支列表转换（conditionNodes/parallelNodes/inclusiveNodes/routeNodes 同构复用）
   */
  const transformBranchListToBackend = (list: FlowlongConditionNode[] | undefined) => {
    if (!list) return undefined
    return list.map(cn => ({
      nodeName: cn.nodeName,
      nodeKey: cn.nodeKey,
      type: cn.type,
      priorityLevel: cn.priorityLevel,
      conditionMode: cn.conditionMode,
      conditionList: cn.conditionList,
      childNode: transformDesignerToBackendNode(cn.childNode)
    })) as any
  }

  const transformBranchListToDesigner = (list: FlowlongConditionNode[] | undefined) => {
    if (!list) return undefined
    return list.map(cn => ({
      nodeName: cn.nodeName,
      nodeKey: cn.nodeKey,
      type: cn.type,
      priorityLevel: cn.priorityLevel || 1,
      conditionMode: cn.conditionMode || 1,
      conditionList: cn.conditionList || [],
      childNode: transformBackendToDesignerNode(cn.childNode)
    })) as any
  }

  /**
   * 定时器设计器态 → extendConfig.time 字符串
   */
  const composeTimerTime = (node: FlowlongNodeModel): string | undefined => {
    if (node.timerMode === 'clock') {
      return node.timerClock
    }
    if (node.timerValue !== undefined && node.timerValue > 0 && node.timerUnit) {
      return `${node.timerValue}:${node.timerUnit}`
    }
    return undefined
  }

  /**
   * extendConfig.time 字符串 → 定时器设计器态
   */
  const parseTimerTime = (time: unknown, result: FlowlongNodeModel) => {
    if (typeof time !== 'string' || !time) return
    const duration = time.match(DURATION_TIME_RE)
    if (duration) {
      result.timerMode = 'duration'
      result.timerValue = Number(duration[1])
      result.timerUnit = duration[2] as 'd' | 'h' | 'm'
      return
    }
    const clock = time.match(CLOCK_TIME_RE)
    if (clock) {
      result.timerMode = 'clock'
      result.timerClock = time
    }
  }

  const transformDesignerToBackendNode = (node: FlowlongNodeModel | undefined): FlowlongNodeModel | undefined => {
    if (!node) return undefined

    const result: FlowlongNodeModel = {
      nodeName: node.nodeName,
      nodeKey: node.nodeKey,
      type: node.type,
      setType: node.setType,
      nodeAssigneeList: node.nodeAssigneeList as any, // 双历史模型字段同构,精确断言由运行时保证
      childNode: transformDesignerToBackendNode(node.childNode)
    }
    // 票签权重随参与人透传（非票签时引擎忽略该字段）
    if (node.nodeAssigneeList?.some(a => a.weight !== undefined)) {
      result.nodeAssigneeList = node.nodeAssigneeList.map(a => ({
        id: a.id, name: a.name, ...(a.weight !== undefined ? { weight: a.weight } : {})
      })) as any
    }

    // 审批节点配置（type=1 审批节点, type=3 条件审批节点）
    if (node.type === 1 || node.type === 3) {
      if (node.examineMode !== undefined) result.examineMode = node.examineMode
      if (node.passWeight !== undefined) result.passWeight = node.passWeight
      if (node.termAuto !== undefined) result.termAuto = node.termAuto
      if (node.term !== undefined) result.term = node.term
      if (node.termMode !== undefined) result.termMode = node.termMode
      if (node.userSelectFlag !== undefined) result.userSelectFlag = node.userSelectFlag
      if (node.expression !== undefined) result.expression = node.expression
      if (node.examineLevel !== undefined) result.examineLevel = node.examineLevel
      if (node.directorLevel !== undefined) result.directorLevel = node.directorLevel
      if (node.directorMode !== undefined) result.directorMode = node.directorMode
      // 驳回策略（引擎顶层字段）
      if (node.rejectStrategy !== undefined) result.rejectStrategy = node.rejectStrategy
      if (node.rejectStart !== undefined) result.rejectStart = node.rejectStart

      // extendConfig 用于存储扩展配置（超时提醒、AI审批、条件列表等）
      const extendConfig: Record<string, any> = {}

      // 超时提醒配置放入 extendConfig
      if (node.remindAuto !== undefined) extendConfig.remindAuto = node.remindAuto
      if (node.remindAdvanceMinutes !== undefined) extendConfig.remindAdvanceMinutes = node.remindAdvanceMinutes
      if (node.remindIntervalHours !== undefined) extendConfig.remindIntervalHours = node.remindIntervalHours
      if (node.remindChannels !== undefined) extendConfig.remindChannels = node.remindChannels

      // AI 审批配置放入 extendConfig.aiApproval
      if (node.aiApproval && node.aiApprovalConfig) {
        extendConfig.aiApproval = node.aiApprovalConfig
      }

      // 条件审批（type=3）条件列表放入 extendConfig.conditionList
      if (node.type === 3 && node.conditionList !== undefined) {
        extendConfig.conditionList = node.conditionList
      }

      // 驳回策略3的目标节点（封装层语义）放入 extendConfig.rejectTargetKey
      if (node.rejectStrategy === 3 && node.rejectTargetKey) {
        extendConfig.rejectTargetKey = node.rejectTargetKey
      }

      // 如果有扩展配置，添加到 result
      if (Object.keys(extendConfig).length > 0) {
        result.extendConfig = extendConfig
      }
    }

    // 分支容器：条件(4)/并行(8)/包容(9)/路由(23)，四类分支同构转换
    if (node.type === 4) result.conditionNodes = transformBranchListToBackend(node.conditionNodes)
    if (node.type === 8) result.parallelNodes = transformBranchListToBackend(node.parallelNodes)
    if (node.type === 9) result.inclusiveNodes = transformBranchListToBackend(node.inclusiveNodes)
    if (node.type === 23) result.routeNodes = transformBranchListToBackend(node.routeNodes)

    // AI 智能路由：网关启用时落 callAi 标记 + extendConfig.aiRouting
    //（aiRouting 配置不可为空对象——引擎部署时以 NON_EMPTY 序列化重写模型，空键会被整键剔除，
    //  故空提示词注入默认文案、数值字段兜底默认值，保证至少一个非空字段落库）
    if ((node.type === 4 || node.type === 9 || node.type === 23) && node.aiRouting) {
      result.callAi = '1'
      const gatewayExt: Record<string, any> = { ...(result.extendConfig || {}) }
      gatewayExt.aiRouting = {
        prompt: node.aiRoutingConfig?.prompt || '根据流程变量为当前单据选择最合适的分支',
        modelName: node.aiRoutingConfig?.modelName || undefined,
        timeoutSeconds: node.aiRoutingConfig?.timeoutSeconds ?? 30,
        confidenceThreshold: node.aiRoutingConfig?.confidenceThreshold ?? 80
      }
      result.extendConfig = gatewayExt
    }

    // 子流程（type=5）
    if (node.type === 5) {
      if (node.callProcess !== undefined) result.callProcess = node.callProcess
      if (node.callAsync !== undefined) result.callAsync = node.callAsync
    }

    // 定时器（type=6）：设计器态合成 extendConfig.time
    if (node.type === 6) {
      const time = composeTimerTime(node)
      if (time) {
        result.extendConfig = { ...(result.extendConfig || {}), time, delayType: 1 }
      }
    }

    // 触发器任务（type=7）配置放入 extendConfig
    if (node.type === 7) {
      const triggerConfig: Record<string, any> = {}
      if (node.triggerType !== undefined) triggerConfig.triggerType = node.triggerType
      if (node.triggerExpression !== undefined) triggerConfig.triggerExpression = node.triggerExpression
      if (node.triggerBean !== undefined) triggerConfig.triggerBean = node.triggerBean
      if (node.triggerClass !== undefined) triggerConfig.triggerClass = node.triggerClass
      if (node.triggerMethod !== undefined) triggerConfig.triggerMethod = node.triggerMethod
      if (node.continueOnError !== undefined) triggerConfig.continueOnError = node.continueOnError
      if (Object.keys(triggerConfig).length > 0) {
        result.extendConfig = { ...(result.extendConfig || {}), ...triggerConfig }
      }
    }

    return result
  }

  /**
   * 将设计器格式转换为 FlowLong 引擎格式
   * 类型值一致，直接传递
   */
  const transformDesignerToBackend = (designerModel: FlowlongProcessModel): FlowlongNodeModel => {
    return transformDesignerToBackendNode(designerModel.nodeConfig)!
  }

  const transformBackendToDesignerNode = (node: FlowlongNodeModel | undefined): FlowlongNodeModel | undefined => {
    if (!node) return undefined

    const result: FlowlongNodeModel = {
      nodeName: node.nodeName,
      nodeKey: node.nodeKey,
      type: node.type,
      setType: node.setType,
      nodeAssigneeList: node.nodeAssigneeList as any, // 双历史模型字段同构
      childNode: transformBackendToDesignerNode(node.childNode)
    }
    // 票签权重随参与人还原
    if (node.nodeAssigneeList?.some((a: any) => a.weight !== undefined)) {
      result.nodeAssigneeList = node.nodeAssigneeList.map((a: any) => ({
        id: a.id, name: a.name, ...(a.weight !== undefined ? { weight: a.weight } : {})
      })) as any
    }

    // 基础审批配置
    if (node.examineMode !== undefined) result.examineMode = node.examineMode
    if (node.passWeight !== undefined) result.passWeight = node.passWeight
    if (node.termAuto !== undefined) result.termAuto = node.termAuto
    if (node.term !== undefined) result.term = node.term
    if (node.termMode !== undefined) result.termMode = node.termMode
    if (node.userSelectFlag !== undefined) result.userSelectFlag = node.userSelectFlag
    if (node.expression !== undefined) result.expression = node.expression
    if (node.examineLevel !== undefined) result.examineLevel = node.examineLevel
    if (node.directorLevel !== undefined) result.directorLevel = node.directorLevel
    if (node.directorMode !== undefined) result.directorMode = node.directorMode
    // 驳回策略（引擎顶层字段）
    if (node.rejectStrategy !== undefined) result.rejectStrategy = node.rejectStrategy
    if (node.rejectStart !== undefined) result.rejectStart = node.rejectStart

    // 从 extendConfig 解析扩展配置
    if (node.extendConfig) {
      // 超时提醒配置
      if (node.extendConfig.remindAuto !== undefined) result.remindAuto = node.extendConfig.remindAuto as boolean
      if (node.extendConfig.remindAdvanceMinutes !== undefined) result.remindAdvanceMinutes = node.extendConfig.remindAdvanceMinutes as number
      if (node.extendConfig.remindIntervalHours !== undefined) result.remindIntervalHours = node.extendConfig.remindIntervalHours as number
      if (node.extendConfig.remindChannels !== undefined) result.remindChannels = node.extendConfig.remindChannels as string[]

      // AI 审批配置
      if (node.extendConfig.aiApproval) {
        result.aiApproval = true
        result.aiApprovalConfig = node.extendConfig.aiApproval as FlowlongAiApprovalConfig
      }

      // AI 智能路由配置（条件/包容/路由网关，callAi 为引擎启用标记）
      if (node.extendConfig.aiRouting && node.callAi) {
        result.aiRouting = true
        result.aiRoutingConfig = node.extendConfig.aiRouting as FlowlongAiRoutingConfig
      }

      // 条件审批（type=3）条件列表
      if (node.type === 3 && node.extendConfig.conditionList !== undefined) {
        result.conditionList = node.extendConfig.conditionList as FlowlongCondition[][]
      }

      // 驳回策略3目标节点
      if ((node.type === 1 || node.type === 3) && node.extendConfig.rejectTargetKey !== undefined) {
        result.rejectTargetKey = node.extendConfig.rejectTargetKey as string
      }

      // 触发器任务(type=7)多模式配置从 extendConfig 读出
      if (node.type === 7) {
        if (node.extendConfig.triggerType !== undefined) result.triggerType = node.extendConfig.triggerType as string
        if (node.extendConfig.triggerExpression !== undefined) result.triggerExpression = node.extendConfig.triggerExpression as string
        if (node.extendConfig.triggerBean !== undefined) result.triggerBean = node.extendConfig.triggerBean as string
        if (node.extendConfig.triggerClass !== undefined) result.triggerClass = node.extendConfig.triggerClass as string
        if (node.extendConfig.triggerMethod !== undefined) result.triggerMethod = node.extendConfig.triggerMethod as string
        if (node.extendConfig.continueOnError !== undefined) result.continueOnError = node.extendConfig.continueOnError as boolean
      }

      // 定时器（type=6）extendConfig.time 解析为设计器态
      if (node.type === 6) {
        parseTimerTime(node.extendConfig.time, result)
      }
    }

    // 分支容器：条件(4)/并行(8)/包容(9)/路由(23)
    if (node.type === 4) result.conditionNodes = transformBranchListToDesigner(node.conditionNodes)
    if (node.type === 8) result.parallelNodes = transformBranchListToDesigner(node.parallelNodes)
    if (node.type === 9) result.inclusiveNodes = transformBranchListToDesigner(node.inclusiveNodes)
    if (node.type === 23) result.routeNodes = transformBranchListToDesigner(node.routeNodes)

    // 子流程（type=5）
    if (node.type === 5) {
      if (node.callProcess !== undefined) result.callProcess = node.callProcess
      if (node.callAsync !== undefined) result.callAsync = node.callAsync
    }

    return result
  }

  /**
   * 将 FlowLong 后端格式转换为设计器格式
   */
  const transformBackendToDesigner = (backendModel: FlowlongNodeModel): FlowlongProcessModel => {
    return { name: '', key: '', nodeConfig: transformBackendToDesignerNode(backendModel)! }
  }

  /**
   * 创建初始模型（发起人节点）
   */
  const createInitialModel = (processKey: string, processName: string): FlowlongProcessModel => {
    const startNode: FlowlongNodeModel = {
      nodeName: '发起人',
      nodeKey: 'start_' + Date.now(),
      type: 0, // TaskType.major = 0 (主办/发起人节点)
      nodeAssigneeList: [],
      childNode: undefined
    }
    return { name: processName, key: processKey, nodeConfig: startNode }
  }

  return {
    transformDesignerToBackend,
    transformBackendToDesigner,
    createInitialModel
  }
}

import { ref, shallowRef, markRaw } from 'vue'

/**
 * FlowLong 流程设计器数据结构类型定义
 */
export interface FlowLongNodeModel {
  nodeName: string
  nodeKey: string
  nodeState?: number
  type: number // 1=开始, 2=审批, 3=抄送, 4=结束, 5=条件, 6=并行, 7=包容
  setType?: number
  nodeAssigneeList?: FlowLongNodeAssignee[]
  nodeCandidate?: FlowLongNodeCandidate
  conditionNodes?: FlowLongConditionNode[]
  parallelNodes?: FlowLongConditionNode[]
  inclusiveNodes?: FlowLongConditionNode[]
  childNode?: FlowLongNodeModel
  parentNode?: FlowLongNodeModel
  extendConfig?: Record<string, any>
  approveSelf?: number
  term?: number
  termMode?: number
  groupStrategy?: number
  remind?: boolean
  allowSelection?: boolean
  allowTransfer?: boolean
  allowAppendNode?: boolean
  allowRollback?: boolean
  allowCc?: boolean
}

export interface FlowLongNodeAssignee {
  label: string
  value: string
  type?: number // 0=用户, 1=角色, 2=部门
}

export interface FlowLongNodeCandidate {
  users?: string[]
  roles?: string[]
  departments?: string[]
  initiator?: boolean
  directorLevel?: number
  strategy?: number // 候选人策略
}

export interface FlowLongConditionNode {
  nodeId: string
  nodeName: string
  conditionList?: FlowLongCondition[]
  childNode?: FlowLongNodeModel
}

export interface FlowLongCondition {
  key: string
  type: string
  value: any
}

export interface FlowLongProcessModel {
  name: string
  key: string
  instanceUrl?: string
  nodeConfig: FlowLongNodeModel
  extendConfig?: Record<string, any>
}

/**
 * FlowLong 流程设计器 Hook
 */
export function useFlowLongDesigner() {
  const modelData = shallowRef<FlowLongProcessModel | null>(null)
  const isReady = ref(false)

  /**
   * 初始化设计器
   */
  const init = (processKey: string, processName: string) => {
    modelData.value = createInitialModel(processKey, processName)
    isReady.value = true
  }

  /**
   * 加载已有流程模型
   */
  const loadModel = (modelJson: string) => {
    try {
      const data = JSON.parse(modelJson) as FlowLongProcessModel
      modelData.value = data
      isReady.value = true
    } catch (err) {
      console.error('加载 FlowLong 模型失败:', err)
      throw err
    }
  }

  /**
   * 获取流程模型 JSON
   */
  const getModelJson = (): string | null => {
    if (!modelData.value) return null
    // 清理 parentNode（避免循环引用导致序列化问题）
    const cleanModel = cleanParentNode(modelData.value)
    return JSON.stringify(cleanModel, null, 2)
  }

  /**
   * 更新流程模型
   */
  const updateModel = (data: FlowLongProcessModel) => {
    modelData.value = markRaw(data)
  }

  /**
   * 清理父节点引用（避免序列化循环引用）
   */
  const cleanParentNode = (model: FlowLongProcessModel): FlowLongProcessModel => {
    const cleanNode = (node: FlowLongNodeModel | undefined): FlowLongNodeModel | undefined => {
      if (!node) return undefined
      const result: FlowLongNodeModel = { ...node, parentNode: undefined }
      if (result.childNode) {
        result.childNode = cleanNode(result.childNode)
      }
      if (result.conditionNodes) {
        result.conditionNodes = result.conditionNodes.map(cn => ({
          ...cn,
          childNode: cleanNode(cn.childNode)
        }))
      }
      return result
    }
    return {
      ...model,
      nodeConfig: cleanNode(model.nodeConfig)!
    }
  }

  /**
   * 创建初始流程模型（开始节点 -> 结束节点）
   */
  const createInitialModel = (processKey: string, processName: string): FlowLongProcessModel => {
    const startNode: FlowLongNodeModel = {
      nodeName: '开始',
      nodeKey: 'start',
      type: 1, // 开始节点类型
      childNode: {
        nodeName: '结束',
        nodeKey: 'end',
        type: 4, // 结束节点类型
      }
    }
    // 设置父节点关系
    startNode.childNode!.parentNode = startNode

    return {
      name: processName,
      key: processKey,
      nodeConfig: startNode
    }
  }

  /**
   * 添加审批节点
   */
  const addApprovalNode = (parentNode: FlowLongNodeModel, nodeName: string = '审批') => {
    const newNode: FlowLongNodeModel = {
      nodeName,
      nodeKey: generateNodeKey('task'),
      type: 2, // 审批节点类型
      parentNode,
      childNode: parentNode.childNode
    }
    if (parentNode.childNode) {
      parentNode.childNode.parentNode = newNode
    }
    parentNode.childNode = newNode
    return newNode
  }

  /**
   * 添加抄送节点
   */
  const addCcNode = (parentNode: FlowLongNodeModel, nodeName: string = '抄送') => {
    const newNode: FlowLongNodeModel = {
      nodeName,
      nodeKey: generateNodeKey('cc'),
      type: 3, // 抄送节点类型
      parentNode,
      childNode: parentNode.childNode
    }
    if (parentNode.childNode) {
      parentNode.childNode.parentNode = newNode
    }
    parentNode.childNode = newNode
    return newNode
  }

  /**
   * 删除节点
   */
  const deleteNode = (node: FlowLongNodeModel) => {
    if (!node.parentNode) return
    // 将父节点的 childNode 指向当前节点的 childNode
    node.parentNode.childNode = node.childNode
    if (node.childNode) {
      node.childNode.parentNode = node.parentNode
    }
  }

  /**
   * 生成节点 Key
   */
  const generateNodeKey = (prefix: string): string => {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).substr(2, 4)}`
  }

  /**
   * 重置设计器
   */
  const reset = () => {
    modelData.value = null
    isReady.value = false
  }

  return {
    modelData,
    isReady,
    init,
    loadModel,
    getModelJson,
    updateModel,
    addApprovalNode,
    addCcNode,
    deleteNode,
    reset,
    createInitialModel,
    generateNodeKey
  }
}

/**
 * FlowLong 节点类型常量
 */
export const FlowLongNodeType = {
  START: 1,
  APPROVAL: 2,
  CC: 3,
  END: 4,
  CONDITION: 5,
  PARALLEL: 6,
  INCLUSIVE: 7,
  TRIGGER: 8
}

/**
 * FlowLong 候选人策略常量
 */
export const FlowLongCandidateStrategy = {
  USER: 10, // 指定用户
  ROLE: 20, // 指定角色
  DEPARTMENT: 30, // 指定部门
  DEPARTMENT_LEADER: 40, // 部门负责人
  START_USER: 50, // 发起人自己
  START_USER_DEPT_LEADER: 60, // 发起人部门负责人
  START_USER_LEADER: 70, // 发起人上级领导
  START_USER_SELECT: 80, // 发起人自选
  APPROVE_USER_SELECT: 90, // 审批人自选
  EXPRESSION: 100 // 表达式
}
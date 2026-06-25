import type { FlowLongNodeModel } from './useFlowLongDesigner'

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
 */

// flowlong-designer 的节点类型（与 FlowLong TaskType 一致）
export interface FlowlongNodeModel {
  nodeName: string
  nodeKey: string
  type: number // -1=结束, 0=发起人, 1=审批, 2=抄送, 3=条件审批, 4=条件分支
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
  examineMode?: number // 多人审批方式 1=依次审批, 2=会签, 3=或签
  userSelectFlag?: boolean // 允许发起人自选抄送人
  expression?: string // 表达式
  conditionNodes?: FlowlongConditionNode[]
  conditionList?: FlowlongCondition[][] // 条件列表（条件组数组）
  childNode?: FlowlongNodeModel
}

export interface FlowlongNodeAssignee {
  id: string
  name: string
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

/**
 * 数据转换 Hook
 *
 * 前端和后端使用一致的节点类型值，无需转换
 */
export function useFlowLongDataTransform() {

  /**
   * 将设计器格式转换为 FlowLong 引擎格式
   * 类型值一致，直接传递
   */
  const transformDesignerToBackend = (designerModel: FlowlongProcessModel): FlowLongNodeModel => {
    const transformNode = (node: FlowlongNodeModel | undefined): FlowLongNodeModel | undefined => {
      if (!node) return undefined

      const result: FlowLongNodeModel = {
        nodeName: node.nodeName,
        nodeKey: node.nodeKey,
        type: node.type,
        setType: node.setType,
        nodeAssigneeList: node.nodeAssigneeList,
        childNode: transformNode(node.childNode)
      }

      // 审批节点配置（type=1 审批节点, type=3 条件审批节点）
      if (node.type === 1 || node.type === 3) {
        if (node.examineMode !== undefined) result.examineMode = node.examineMode
        if (node.termAuto !== undefined) result.termAuto = node.termAuto
        if (node.term !== undefined) result.term = node.term
        if (node.termMode !== undefined) result.termMode = node.termMode
        // 超时提醒配置
        if (node.remindAuto !== undefined) result.remindAuto = node.remindAuto
        if (node.remindAdvanceMinutes !== undefined) result.remindAdvanceMinutes = node.remindAdvanceMinutes
        if (node.remindIntervalHours !== undefined) result.remindIntervalHours = node.remindIntervalHours
        if (node.remindChannels !== undefined) result.remindChannels = node.remindChannels
        if (node.userSelectFlag !== undefined) result.userSelectFlag = node.userSelectFlag
        if (node.expression !== undefined) result.expression = node.expression
        if (node.examineLevel !== undefined) result.examineLevel = node.examineLevel
        if (node.directorLevel !== undefined) result.directorLevel = node.directorLevel
        if (node.directorMode !== undefined) result.directorMode = node.directorMode
      }

      // 条件分支（type=4）
      if (node.type === 4 && node.conditionNodes) {
        result.conditionNodes = node.conditionNodes.map(cn => ({
          nodeName: cn.nodeName,
          nodeKey: cn.nodeKey,
          type: cn.type,
          priorityLevel: cn.priorityLevel,
          conditionMode: cn.conditionMode,
          conditionList: cn.conditionList,
          childNode: transformNode(cn.childNode)
        })) as any
      }

      return result
    }

    return transformNode(designerModel.nodeConfig)!
  }

  /**
   * 将 FlowLong 后端格式转换为设计器格式
   */
  const transformBackendToDesigner = (backendModel: FlowLongNodeModel): FlowlongProcessModel => {
    const transformNode = (node: FlowLongNodeModel | undefined): FlowlongNodeModel | undefined => {
      if (!node) return undefined

      const result: FlowlongNodeModel = {
        nodeName: node.nodeName,
        nodeKey: node.nodeKey,
        type: node.type,
        setType: node.setType,
        nodeAssigneeList: node.nodeAssigneeList,
        childNode: transformNode(node.childNode)
      }

      if (node.examineMode !== undefined) result.examineMode = node.examineMode
      if (node.termAuto !== undefined) result.termAuto = node.termAuto
      if (node.term !== undefined) result.term = node.term
      if (node.termMode !== undefined) result.termMode = node.termMode
      // 超时提醒配置
      if (node.remindAuto !== undefined) result.remindAuto = node.remindAuto
      if (node.remindAdvanceMinutes !== undefined) result.remindAdvanceMinutes = node.remindAdvanceMinutes
      if (node.remindIntervalHours !== undefined) result.remindIntervalHours = node.remindIntervalHours
      if (node.remindChannels !== undefined) result.remindChannels = node.remindChannels
      if (node.userSelectFlag !== undefined) result.userSelectFlag = node.userSelectFlag
      if (node.expression !== undefined) result.expression = node.expression
      if (node.examineLevel !== undefined) result.examineLevel = node.examineLevel
      if (node.directorLevel !== undefined) result.directorLevel = node.directorLevel
      if (node.directorMode !== undefined) result.directorMode = node.directorMode

      if (node.type === 4 && node.conditionNodes) {
        result.conditionNodes = node.conditionNodes.map(cn => ({
          nodeName: cn.nodeName,
          nodeKey: cn.nodeKey,
          type: cn.type,
          priorityLevel: cn.priorityLevel || 1,
          conditionMode: cn.conditionMode || 1,
          conditionList: cn.conditionList || [],
          childNode: transformNode(cn.childNode)
        })) as any
      }

      return result
    }

    return { name: '', key: '', nodeConfig: transformNode(backendModel)! }
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
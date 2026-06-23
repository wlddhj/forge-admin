import type { FlowLongNodeModel } from './useFlowLongDesigner'

/**
 * flowlong-designer 数据类型定义
 */

// flowlong-designer 的节点类型
export interface FlowlongNodeModel {
  nodeName: string
  nodeKey: string
  type: number // 0=发起人, 1=审批人, 2=抄送人, 3=条件, 4=条件路由
  setType?: number // 1=指定成员, 2=主管, 3=角色, 4=发起人自选, 5=发起人自己, 6=审批人自选, 7=连续多级主管, 8=表达式
  nodeAssigneeList?: FlowlongNodeAssignee[]
  examineLevel?: number // 指定主管层级
  directorLevel?: number // 连续主管审批层级
  directorMode?: number // 连续主管审批方式
  selectMode?: number // 发起人自选类型
  termAuto?: boolean // 超时自动审批
  term?: number // 审批期限（小时）
  termMode?: number // 超时后执行类型 0=自动通过, 1=自动拒绝
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
 * 节点类型映射表：flowlong-designer -> forge-admin 后端
 * flowlong-designer: 0=发起人, 1=审批人, 2=抄送人, 3=条件, 4=条件路由
 * forge-admin: 1=开始, 2=审批, 3=抄送, 4=结束, 5=条件
 */
const NODE_TYPE_MAP: Record<number, number> = {
  0: 1, // 发起人 -> 开始节点
  1: 2, // 审批人 -> 审批节点
  2: 3, // 抄送人 -> 抄送节点
  4: 5  // 条件路由 -> 条件分支
}

/**
 * 候选人策略映射表：flowlong-designer setType -> forge-admin CandidateStrategyEnum
 */
const STRATEGY_MAP: Record<number, number> = {
  1: 30, // 指定成员 -> USER
  2: 37, // 部门负责人 -> START_USER_DEPT_LEADER
  3: 10, // 角色 -> ROLE
  4: 35, // 发起人自选 -> START_USER_SELECT
  5: 36, // 发起人自己 -> START_USER
  6: 34, // 审批人自选 -> APPROVE_USER_SELECT
  7: 38, // 连续多级部门负责人 -> DEPT_LEADER_MULTI
  8: 60  // 表达式 -> EXPRESSION
}

/**
 * 反向映射：forge-admin CandidateStrategyEnum -> flowlong-designer setType
 */
const STRATEGY_REVERSE_MAP: Record<number, number> = {
  30: 1,  // USER -> 指定成员
  37: 2,  // START_USER_DEPT_LEADER -> 部门负责人
  10: 3,  // ROLE -> 角色
  35: 4,  // START_USER_SELECT -> 发起人自选
  36: 5,  // START_USER -> 发起人自己
  34: 6,  // APPROVE_USER_SELECT -> 审批人自选
  38: 7,  // DEPT_LEADER_MULTI -> 连续多级部门负责人
  60: 8   // EXPRESSION -> 表达式
}

/**
 * 反向节点类型映射：forge-admin -> flowlong-designer
 */
const NODE_TYPE_REVERSE_MAP: Record<number, number> = {
  1: 0, // 开始节点 -> 发起人
  2: 1, // 审批节点 -> 审批人
  3: 2, // 抄送节点 -> 抄送人
  5: 4  // 条件分支 -> 条件路由
}

/**
 * 数据转换 Hook
 */
export function useFlowLongDataTransform() {
  /**
   * 将 flowlong-designer 格式转换为后端格式
   */
  const transformDesignerToBackend = (designerModel: FlowlongProcessModel): FlowLongNodeModel => {
    const transformNode = (node: FlowlongNodeModel | undefined): FlowLongNodeModel | undefined => {
      if (!node) return undefined

      const result: FlowLongNodeModel = {
        nodeName: node.nodeName,
        nodeKey: node.nodeKey,
        type: NODE_TYPE_MAP[node.type] || node.type,
        childNode: transformNode(node.childNode)
      }

      // 处理审批节点/抄送节点的候选人
      if (node.type === 1 || node.type === 2) {
        const setType = node.setType ?? 1
        const strategy = STRATEGY_MAP[setType] ?? 30

        result.nodeCandidate = {
          strategy,
          users: node.setType === 1 ? node.nodeAssigneeList?.map(a => a.id) : undefined,
          roles: node.setType === 3 ? node.nodeAssigneeList?.map(a => a.id) : undefined,
          initiator: node.setType === 5 ? true : undefined,
          directorLevel: node.setType === 2 ? node.examineLevel : (node.setType === 7 ? node.directorLevel : undefined)
        }

        // 保留审批配置
        result.extendConfig = {
          examineMode: node.examineMode,
          termAuto: node.termAuto,
          term: node.term,
          termMode: node.termMode,
          userSelectFlag: node.userSelectFlag,
          expression: node.expression
        }
      }

      // 处理条件分支
      if (node.type === 4 && node.conditionNodes) {
        result.conditionNodes = node.conditionNodes.map(cn => ({
          nodeId: cn.nodeKey,
          nodeName: cn.nodeName,
          // 设计器使用二维数组（条件组），将一维数组包装成单个条件组
          conditionList: cn.conditionList ? [cn.conditionList] : [],
          childNode: transformNode(cn.childNode)
        })) as any
      }

      return result
    }

    return transformNode(designerModel.nodeConfig)!
  }

  /**
   * 将后端格式转换为 flowlong-designer 格式
   */
  const transformBackendToDesigner = (backendModel: FlowLongNodeModel): FlowlongProcessModel => {
    const transformNode = (node: FlowLongNodeModel | undefined): FlowlongNodeModel | undefined => {
      if (!node) return undefined

      const result: FlowlongNodeModel = {
        nodeName: node.nodeName,
        nodeKey: node.nodeKey,
        type: NODE_TYPE_REVERSE_MAP[node.type] || node.type,
        childNode: transformNode(node.childNode)
      }

      // 处理审批节点/抄送节点的候选人
      if (node.nodeCandidate) {
        const strategy = node.nodeCandidate.strategy ?? 30
        const setType = STRATEGY_REVERSE_MAP[strategy] ?? 1
        result.setType = setType

        // 转换 nodeAssigneeList
        if (setType === 1 && node.nodeCandidate.users) {
          result.nodeAssigneeList = node.nodeCandidate.users.map(id => ({ id, name: '' }))
        } else if (setType === 3 && node.nodeCandidate.roles) {
          result.nodeAssigneeList = node.nodeCandidate.roles.map(id => ({ id, name: '' }))
        }

        // 设置主管层级
        if (setType === 2) {
          result.examineLevel = node.nodeCandidate.directorLevel || 1
        } else if (setType === 7) {
          result.directorLevel = node.nodeCandidate.directorLevel || 1
        }
      }

      // 处理 extendConfig
      if (node.extendConfig) {
        result.examineMode = node.extendConfig.examineMode
        result.termAuto = node.extendConfig.termAuto
        result.term = node.extendConfig.term
        result.termMode = node.extendConfig.termMode
        result.userSelectFlag = node.extendConfig.userSelectFlag
        result.expression = node.extendConfig.expression
      }

      // 处理条件分支
      if (node.type === 5 && node.conditionNodes) {
        result.type = 4 // 条件路由
        result.conditionNodes = node.conditionNodes.map(cn => {
          // 后端使用一维数组，将二维数组展平（取第一个条件组）
          const flatConditionList = (cn.conditionList && cn.conditionList.length > 0)
            ? cn.conditionList.flat()
            : []
          return {
            nodeName: cn.nodeName,
            nodeKey: cn.nodeId,
            type: 3,
            priorityLevel: 1,
            conditionMode: 1,
            conditionList: flatConditionList,
            childNode: transformNode(cn.childNode)
          }
        }) as any
      }

      return result
    }

    return {
      name: '',
      key: '',
      nodeConfig: transformNode(backendModel)!
    }
  }

  /**
   * 创建初始模型（发起人节点）
   */
  const createInitialModel = (processKey: string, processName: string): FlowlongProcessModel => {
    return {
      name: processName,
      key: processKey,
      nodeConfig: {
        nodeName: '发起人',
        nodeKey: 'start_' + Date.now(),
        type: 0,
        nodeAssigneeList: [],
        childNode: undefined
      }
    }
  }

  return {
    transformDesignerToBackend,
    transformBackendToDesigner,
    createInitialModel,
    NODE_TYPE_MAP,
    STRATEGY_MAP
  }
}
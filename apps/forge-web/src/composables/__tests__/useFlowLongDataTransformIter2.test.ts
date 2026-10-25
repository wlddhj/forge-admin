import { describe, it, expect } from 'vitest'
import { useFlowLongDataTransform } from '../useFlowLongDataTransform'
import type { FlowlongNodeModel, FlowlongProcessModel, FlowlongConditionNode } from '../useFlowLongDataTransform'

/**
 * 迭代二新节点类型转换用例：并行/包容/路由分支、子流程、定时器、票签、驳回策略
 */
describe('useFlowLongDataTransform 迭代二扩展', () => {
  const { transformDesignerToBackend, transformBackendToDesigner } = useFlowLongDataTransform()

  const branch = (name: string, key: string, childNode?: FlowlongNodeModel): FlowlongConditionNode => ({
    nodeName: name,
    nodeKey: key,
    type: 3,
    priorityLevel: 1,
    conditionList: [],
    childNode
  })

  const designerModelOf = (nodeConfig: FlowlongNodeModel): FlowlongProcessModel => ({
    name: '测试流程',
    key: 'test_process',
    nodeConfig
  })

  describe('并行/包容/路由分支', () => {
    it('并行分支 parallelNodes 应双向转换且子链递归', () => {
      const designer = designerModelOf({
        nodeName: '并行分支',
        nodeKey: 'pb_1',
        type: 8,
        parallelNodes: [
          branch('分支1', 'b1', { nodeName: '审批A', nodeKey: 'a1', type: 1, setType: 1 }),
          branch('分支2', 'b2', { nodeName: '审批B', nodeKey: 'a2', type: 1, setType: 1 })
        ],
        childNode: { nodeName: '汇聚后', nodeKey: 'next', type: 1, setType: 1 }
      })

      const backend = transformDesignerToBackend(designer)
      expect(backend.parallelNodes).toHaveLength(2)
      expect(backend.parallelNodes![0].childNode?.nodeKey).toBe('a1')

      const roundTrip = transformDesignerToBackend(transformBackendToDesigner(backend))
      // toDesigner 侧 conditionMode 默认补 1（与既有 conditionNodes 行为一致），做语义比较
      expect(roundTrip.parallelNodes).toHaveLength(2)
      expect(roundTrip.parallelNodes!.map(cn => cn.nodeKey)).toEqual(['b1', 'b2'])
      expect(roundTrip.parallelNodes![0].childNode?.nodeKey).toBe('a1')
      expect(roundTrip.parallelNodes![1].childNode?.nodeKey).toBe('a2')
      expect(roundTrip.childNode?.nodeKey).toBe('next')
    })

    it('包容分支 inclusiveNodes 双向一致', () => {
      const designer = designerModelOf({
        nodeName: '包容分支', nodeKey: 'ib_1', type: 9,
        inclusiveNodes: [branch('分支1', 'b1'), branch('兜底', 'b2')],
        childNode: undefined
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.inclusiveNodes).toHaveLength(2)
      const roundTrip = transformDesignerToBackend(transformBackendToDesigner(backend))
      expect(roundTrip.inclusiveNodes).toHaveLength(2)
    })

    it('路由分支 routeNodes 保留 route: 前缀 nodeKey', () => {
      const designer = designerModelOf({
        nodeName: '路由分支', nodeKey: 'rb_1', type: 23,
        routeNodes: [{ ...branch('金额大于1万', 'route:node_5'), priorityLevel: 1, conditionList: [[{ label: '金额', field: 'amount', operator: '>', value: '10000' }]] }],
        childNode: undefined
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.routeNodes![0].nodeKey).toBe('route:node_5')
      const roundTrip = transformDesignerToBackend(transformBackendToDesigner(backend))
      expect(roundTrip.routeNodes![0].nodeKey).toBe('route:node_5')
      expect(roundTrip.routeNodes![0].conditionList![0][0].field).toBe('amount')
    })

    it('旧模型：分支容器缺分支数组字段不报错', () => {
      const backend: FlowlongNodeModel = { nodeName: '并行', nodeKey: 'pb', type: 8, childNode: undefined }
      const designer = transformBackendToDesigner(backend)
      expect(designer.nodeConfig.type).toBe(8)
      expect(designer.nodeConfig.parallelNodes).toBeUndefined()
    })
  })

  describe('子流程', () => {
    it('callProcess 顶层透传且双向一致', () => {
      const designer = designerModelOf({
        nodeName: '子流程', nodeKey: 'cp_1', type: 5,
        callProcess: '12:报销子流程',
        childNode: { nodeName: '下一节点', nodeKey: 'next', type: 1, setType: 1 }
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.callProcess).toBe('12:报销子流程')
      const roundTrip = transformDesignerToBackend(transformBackendToDesigner(backend))
      expect(roundTrip.callProcess).toBe('12:报销子流程')
      expect(roundTrip.childNode?.nodeKey).toBe('next')
    })
  })

  describe('定时器', () => {
    it('时长式设计器态合成 extendConfig.time 并可解析回来', () => {
      const designer = designerModelOf({
        nodeName: '等待', nodeKey: 'tm_1', type: 6,
        timerMode: 'duration', timerValue: 2, timerUnit: 'h',
        childNode: undefined
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.extendConfig?.time).toBe('2:h')
      expect(backend.extendConfig?.delayType).toBe(1)

      const designerBack = transformBackendToDesigner(backend)
      expect(designerBack.nodeConfig.timerMode).toBe('duration')
      expect(designerBack.nodeConfig.timerValue).toBe(2)
      expect(designerBack.nodeConfig.timerUnit).toBe('h')
    })

    it('时刻式 HH:mm:ss 双向', () => {
      const designer = designerModelOf({
        nodeName: '定点', nodeKey: 'tm_2', type: 6,
        timerMode: 'clock', timerClock: '01:30:00',
        childNode: undefined
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.extendConfig?.time).toBe('01:30:00')
      const designerBack = transformBackendToDesigner(backend)
      expect(designerBack.nodeConfig.timerMode).toBe('clock')
      expect(designerBack.nodeConfig.timerClock).toBe('01:30:00')
    })

    it('非法 time 字符串静默忽略', () => {
      const backend: FlowlongNodeModel = {
        nodeName: '坏数据', nodeKey: 'tm_3', type: 6,
        extendConfig: { time: 'abc' }, childNode: undefined
      }
      const designer = transformBackendToDesigner(backend)
      expect(designer.nodeConfig.timerMode).toBeUndefined()
    })
  })

  describe('票签与驳回策略', () => {
    it('票签：examineMode/passWeight/参与人权重双向一致', () => {
      const designer = designerModelOf({
        nodeName: '投票', nodeKey: 'vote_1', type: 1, setType: 1,
        examineMode: 4, passWeight: 60,
        nodeAssigneeList: [
          { id: '1', name: '甲', weight: 50 },
          { id: '2', name: '乙', weight: 30 },
          { id: '3', name: '丙', weight: 20 }
        ],
        childNode: undefined
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.examineMode).toBe(4)
      expect(backend.passWeight).toBe(60)
      expect(backend.nodeAssigneeList?.[0].weight).toBe(50)

      const roundTrip = transformDesignerToBackend(transformBackendToDesigner(backend))
      expect(roundTrip.passWeight).toBe(60)
      expect(roundTrip.nodeAssigneeList?.map(a => a.weight)).toEqual([50, 30, 20])
    })

    it('驳回策略：rejectStrategy/rejectStart 顶层，策略3目标进 extendConfig', () => {
      const designer = designerModelOf({
        nodeName: '审批', nodeKey: 'ap_1', type: 1, setType: 1,
        rejectStrategy: 3, rejectStart: 2, rejectTargetKey: 'node_2',
        childNode: undefined
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.rejectStrategy).toBe(3)
      expect(backend.rejectStart).toBe(2)
      expect(backend.extendConfig?.rejectTargetKey).toBe('node_2')

      const designerBack = transformBackendToDesigner(backend)
      expect(designerBack.nodeConfig.rejectStrategy).toBe(3)
      expect(designerBack.nodeConfig.rejectTargetKey).toBe('node_2')
    })

    it('非策略3不写 rejectTargetKey，旧审批节点无新字段不受影响', () => {
      const designer = designerModelOf({
        nodeName: '审批', nodeKey: 'ap_2', type: 1, setType: 1,
        rejectStrategy: 2, rejectTargetKey: 'node_9',
        childNode: undefined
      })
      const backend = transformDesignerToBackend(designer)
      expect(backend.extendConfig?.rejectTargetKey).toBeUndefined()

      const legacy: FlowlongNodeModel = { nodeName: '旧审批', nodeKey: 'old_1', type: 1, setType: 1, examineMode: 2, childNode: undefined }
      const roundTrip = transformDesignerToBackend(transformBackendToDesigner(legacy))
      expect(roundTrip.examineMode).toBe(2)
      expect(roundTrip.rejectStrategy).toBeUndefined()
    })
  })
})

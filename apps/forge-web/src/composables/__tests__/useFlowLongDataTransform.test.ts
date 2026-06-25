import { describe, it, expect } from 'vitest'
import { useFlowLongDataTransform } from '../useFlowLongDataTransform'
import type { FlowlongNodeModel, FlowlongProcessModel } from '../useFlowLongDataTransform'

describe('useFlowLongDataTransform', () => {
  const { transformDesignerToBackend, transformBackendToDesigner, createInitialModel } = useFlowLongDataTransform()

  describe('提醒配置字段转换', () => {
    it('应正确转换提醒配置字段到后端格式', () => {
      const designerModel: FlowlongProcessModel = {
        name: '测试流程',
        key: 'test_process',
        nodeConfig: {
          nodeName: '审批节点',
          nodeKey: 'task_1',
          type: 1, // 审批节点
          setType: 1,
          termAuto: true,
          term: 24,
          termMode: 0,
          remindAuto: true,
          remindAdvanceMinutes: 30,
          remindIntervalHours: 4,
          remindChannels: ['websocket'],
          childNode: undefined
        }
      }

      const result = transformDesignerToBackend(designerModel)

      expect(result.remindAuto).toBe(true)
      expect(result.remindAdvanceMinutes).toBe(30)
      expect(result.remindIntervalHours).toBe(4)
      expect(result.remindChannels).toEqual(['websocket'])
    })

    it('应正确从后端格式转换提醒配置字段', () => {
      const backendModel: FlowlongNodeModel = {
        nodeName: '审批节点',
        nodeKey: 'task_1',
        type: 1,
        setType: 1,
        termAuto: true,
        term: 24,
        termMode: 0,
        remindAuto: true,
        remindAdvanceMinutes: 60,
        remindIntervalHours: 12,
        remindChannels: ['websocket', 'email'],
        childNode: undefined
      }

      const result = transformBackendToDesigner(backendModel)

      expect(result.nodeConfig.remindAuto).toBe(true)
      expect(result.nodeConfig.remindAdvanceMinutes).toBe(60)
      expect(result.nodeConfig.remindIntervalHours).toBe(12)
      expect(result.nodeConfig.remindChannels).toEqual(['websocket', 'email'])
    })

    it('未配置提醒时不应包含提醒字段', () => {
      const designerModel: FlowlongProcessModel = {
        name: '测试流程',
        key: 'test_process',
        nodeConfig: {
          nodeName: '审批节点',
          nodeKey: 'task_1',
          type: 1,
          setType: 1,
          childNode: undefined
        }
      }

      const result = transformDesignerToBackend(designerModel)

      expect(result.remindAuto).toBeUndefined()
      expect(result.remindAdvanceMinutes).toBeUndefined()
      expect(result.remindIntervalHours).toBeUndefined()
      expect(result.remindChannels).toBeUndefined()
    })

    it('remindAuto 为 false 时不应设置其他提醒字段', () => {
      const designerModel: FlowlongProcessModel = {
        name: '测试流程',
        key: 'test_process',
        nodeConfig: {
          nodeName: '审批节点',
          nodeKey: 'task_1',
          type: 1,
          remindAuto: false,
          childNode: undefined
        }
      }

      const result = transformDesignerToBackend(designerModel)

      expect(result.remindAuto).toBe(false)
    })

    it('应正确处理嵌套节点中的提醒配置', () => {
      const designerModel: FlowlongProcessModel = {
        name: '测试流程',
        key: 'test_process',
        nodeConfig: {
          nodeName: '发起人',
          nodeKey: 'start',
          type: 0,
          childNode: {
            nodeName: '审批节点',
            nodeKey: 'task_1',
            type: 1,
            remindAuto: true,
            remindAdvanceMinutes: 30,
            remindIntervalHours: 8,
            remindChannels: ['websocket'],
            childNode: {
              nodeName: '结束',
              nodeKey: 'end',
              type: -1
            }
          }
        }
      }

      const result = transformDesignerToBackend(designerModel)

      expect(result.childNode?.remindAuto).toBe(true)
      expect(result.childNode?.remindAdvanceMinutes).toBe(30)
      expect(result.childNode?.remindIntervalHours).toBe(8)
      expect(result.childNode?.remindChannels).toEqual(['websocket'])
    })

    it('应正确处理多提醒渠道', () => {
      const designerModel: FlowlongProcessModel = {
        name: '测试流程',
        key: 'test_process',
        nodeConfig: {
          nodeName: '审批节点',
          nodeKey: 'task_1',
          type: 1,
          remindAuto: true,
          remindChannels: ['websocket', 'email', 'sms'],
          childNode: undefined
        }
      }

      const result = transformDesignerToBackend(designerModel)

      expect(result.remindChannels).toEqual(['websocket', 'email', 'sms'])
    })
  })

  describe('超时自动审批配置字段转换', () => {
    it('应正确转换超时配置字段', () => {
      const designerModel: FlowlongProcessModel = {
        name: '测试流程',
        key: 'test_process',
        nodeConfig: {
          nodeName: '审批节点',
          nodeKey: 'task_1',
          type: 1,
          termAuto: true,
          term: 48,
          termMode: 1, // 自动拒绝
          childNode: undefined
        }
      }

      const result = transformDesignerToBackend(designerModel)

      expect(result.termAuto).toBe(true)
      expect(result.term).toBe(48)
      expect(result.termMode).toBe(1)
    })
  })

  describe('双向转换一致性', () => {
    it('转换后端到设计器再到后端应保持一致', () => {
      const backendModel: FlowlongNodeModel = {
        nodeName: '审批节点',
        nodeKey: 'task_1',
        type: 1,
        setType: 1,
        termAuto: true,
        term: 24,
        termMode: 0,
        remindAuto: true,
        remindAdvanceMinutes: 30,
        remindIntervalHours: 6,
        remindChannels: ['websocket'],
        examineMode: 2,
        childNode: undefined
      }

      const designerModel = transformBackendToDesigner(backendModel)
      const resultModel = transformDesignerToBackend(designerModel)

      expect(resultModel.remindAuto).toBe(backendModel.remindAuto)
      expect(resultModel.remindAdvanceMinutes).toBe(backendModel.remindAdvanceMinutes)
      expect(resultModel.remindIntervalHours).toBe(backendModel.remindIntervalHours)
      expect(resultModel.remindChannels).toEqual(backendModel.remindChannels)
      expect(resultModel.termAuto).toBe(backendModel.termAuto)
      expect(resultModel.term).toBe(backendModel.term)
      expect(resultModel.termMode).toBe(backendModel.termMode)
      expect(resultModel.examineMode).toBe(backendModel.examineMode)
    })
  })

  describe('创建初始模型', () => {
    it('应创建包含发起人节点的初始模型', () => {
      const model = createInitialModel('test_key', '测试流程')

      expect(model.name).toBe('测试流程')
      expect(model.key).toBe('test_key')
      expect(model.nodeConfig.nodeName).toBe('发起人')
      expect(model.nodeConfig.type).toBe(0)
    })
  })
})
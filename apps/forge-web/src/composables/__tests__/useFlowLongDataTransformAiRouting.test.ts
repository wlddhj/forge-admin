import { describe, it, expect } from 'vitest'
import { useFlowLongDataTransform } from '../useFlowLongDataTransform'
import type { FlowlongNodeModel, FlowlongProcessModel, FlowlongConditionNode } from '../useFlowLongDataTransform'

/**
 * AI 智能路由转换用例：三类网关 callAi 标记 + extendConfig.aiRouting 双向透传
 */
describe('useFlowLongDataTransform AI 智能路由', () => {
  const { transformDesignerToBackend, transformBackendToDesigner } = useFlowLongDataTransform()

  const branch = (name: string, key: string): FlowlongConditionNode => ({
    nodeName: name,
    nodeKey: key,
    type: 3,
    priorityLevel: 1,
    conditionList: [],
    childNode: undefined
  })

  const designerModelOf = (nodeConfig: FlowlongNodeModel): FlowlongProcessModel => ({
    name: 'AI路由测试流程',
    key: 'test_ai_routing',
    nodeConfig
  })

  it.each([4, 9, 23])('type=%i 网关启用 AI 路由：落 callAi 标记与 extendConfig.aiRouting', (type) => {
    const listKey = type === 4 ? 'conditionNodes' : type === 9 ? 'inclusiveNodes' : 'routeNodes'
    const designer = designerModelOf({
      nodeName: '网关',
      nodeKey: 'gw_1',
      type,
      aiRouting: true,
      aiRoutingConfig: { prompt: '金额大于1万走高额', modelName: 'deepseek-chat', timeoutSeconds: 20, confidenceThreshold: 70 },
      [listKey]: [branch('分支1', 'b1'), branch('默认', 'b_def')]
    } as FlowlongNodeModel)

    const backend = transformDesignerToBackend(designer)
    expect(backend.callAi).toBe('1')
    const ext = backend.extendConfig as Record<string, any>
    expect(ext.aiRouting).toMatchObject({
      prompt: '金额大于1万走高额',
      modelName: 'deepseek-chat',
      timeoutSeconds: 20,
      confidenceThreshold: 70
    })

    const designer2 = transformBackendToDesigner(backend).nodeConfig
    expect(designer2.aiRouting).toBe(true)
    expect(designer2.aiRoutingConfig).toMatchObject({ prompt: '金额大于1万走高额', timeoutSeconds: 20 })
  })

  it('空提示词与缺省数值注入默认值（防空配置被引擎 NON_EMPTY 序列化剔除）', () => {
    const designer = designerModelOf({
      nodeName: '条件分支',
      nodeKey: 'gw_2',
      type: 4,
      aiRouting: true,
      aiRoutingConfig: {},
      conditionNodes: [branch('分支1', 'b1'), branch('默认', 'b_def')]
    })

    const backend = transformDesignerToBackend(designer)
    const ext = backend.extendConfig as Record<string, any>
    expect(ext.aiRouting.prompt).toBeTruthy()
    expect(ext.aiRouting.timeoutSeconds).toBe(15)
    expect(ext.aiRouting.confidenceThreshold).toBe(80)
  })

  it('未启用 AI 路由：不落 callAi/aiRouting，旧模型零影响', () => {
    const designer = designerModelOf({
      nodeName: '条件分支',
      nodeKey: 'gw_3',
      type: 4,
      conditionNodes: [branch('分支1', 'b1'), branch('默认', 'b_def')]
    })

    const backend = transformDesignerToBackend(designer)
    expect(backend.callAi).toBeUndefined()
    expect(backend.extendConfig).toBeUndefined()
  })

  it('并行分支(8)不适用 AI 路由：即使误配置也不落 callAi', () => {
    const designer = designerModelOf({
      nodeName: '并行分支',
      nodeKey: 'pb_1',
      type: 8,
      aiRouting: true,
      aiRoutingConfig: { prompt: '不该出现' },
      parallelNodes: [branch('分支1', 'p1'), branch('分支2', 'p2')]
    } as FlowlongNodeModel)

    const backend = transformDesignerToBackend(designer)
    expect(backend.callAi).toBeUndefined()
  })

  it('后端旧模型仅 callAi 无 aiRouting 配置：不误判为启用', () => {
    const designer = transformBackendToDesigner({
      nodeName: '条件分支',
      nodeKey: 'gw_4',
      type: 4,
      callAi: '1',
      conditionNodes: [branch('分支1', 'b1')]
    } as FlowlongNodeModel).nodeConfig
    expect(designer.aiRouting).toBeUndefined()
  })
})

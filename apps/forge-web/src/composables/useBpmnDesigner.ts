import { ref, shallowRef, markRaw, type Ref } from 'vue'
import LogicFlow from '@logicflow/core'
import { BPMNElements, BpmnXmlAdapter } from '@logicflow/extension'

/** BPMN 元素类型映射 */
const BPMN_TYPE_MAP: Record<string, string> = {
  startEvent: 'bpmn:startEvent',
  endEvent: 'bpmn:endEvent',
  userTask: 'bpmn:userTask',
  serviceTask: 'bpmn:serviceTask',
  exclusiveGateway: 'bpmn:exclusiveGateway',
  parallelGateway: 'bpmn:parallelGateway',
  inclusiveGateway: 'bpmn:inclusiveGateway',
  timerStartEvent: 'bpmn:timerStartEvent',
  timerCatchEvent: 'bpmn:timerCatchEvent',
}

/** 默认元素尺寸 */
const ELEMENT_SIZES: Record<string, { width: number; height: number }> = {
  'bpmn:startEvent': { width: 36, height: 36 },
  'bpmn:endEvent': { width: 36, height: 36 },
  'bpmn:userTask': { width: 100, height: 80 },
  'bpmn:serviceTask': { width: 100, height: 80 },
  'bpmn:exclusiveGateway': { width: 50, height: 50 },
  'bpmn:parallelGateway': { width: 50, height: 50 },
}

/**
 * 解析标准 BPMN 2.0 XML 为 LogicFlow GraphData
 * 处理没有 bpmndi:BPMNDiagram 图形信息的 BPMN XML
 */
export function parseStandardBpmnXml(xml: string) {
  const parser = new DOMParser()
  const doc = parser.parseFromString(xml, 'text/xml')

  const nodes: any[] = []
  const edges: any[] = []
  const nodeMap = new Map<string, { id: string; type: string; name: string; properties: Record<string, string> }>()

  // 收集所有流程元素
  const processEl = doc.querySelector('process')
  if (!processEl) return { nodes: [], edges: [] }

  for (const el of processEl.children) {
    const localName = el.localName

    if (localName === 'sequenceFlow') {
      const sourceRef = el.getAttribute('sourceRef')
      const targetRef = el.getAttribute('targetRef')
      if (sourceRef && targetRef) {
        edges.push({
          id: el.getAttribute('id') || `edge_${sourceRef}_${targetRef}`,
          type: 'bpmn:sequenceFlow',
          sourceNodeId: sourceRef,
          targetNodeId: targetRef,
          text: el.getAttribute('name') || undefined,
        })
      }
    } else {
      const bpmnType = BPMN_TYPE_MAP[localName]
      if (bpmnType) {
        const id = el.getAttribute('id') || ''
        const name = el.getAttribute('name') || id
        const properties: Record<string, string> = {}

        for (const attr of el.attributes) {
          const attrName = attr.name
          if (attrName !== 'id' && attrName !== 'name') {
            properties[attrName] = attr.value
          }
        }

        nodeMap.set(id, { id, type: bpmnType, name, properties })
      }
    }
  }

  // BFS 自动布局：从 startEvent 出发，按层排列
  const levelMap = new Map<string, number>()
  const adjList = new Map<string, string[]>()

  for (const edge of edges) {
    if (!adjList.has(edge.sourceNodeId)) adjList.set(edge.sourceNodeId, [])
    adjList.get(edge.sourceNodeId)!.push(edge.targetNodeId)
  }

  // 找到所有 startEvent 作为起点
  const startNodes = [...nodeMap.values()].filter(n => n.type === 'bpmn:startEvent')
  const queue: { id: string; level: number }[] = startNodes.map(n => ({ id: n.id, level: 0 }))
  const visited = new Set<string>()

  while (queue.length > 0) {
    const { id, level } = queue.shift()!
    if (visited.has(id)) continue
    visited.add(id)
    levelMap.set(id, level)

    const neighbors = adjList.get(id) || []
    for (const nextId of neighbors) {
      if (!visited.has(nextId)) {
        queue.push({ id: nextId, level: level + 1 })
      }
    }
  }

  // 未被 BFS 访问到的孤立节点，追加到最后一层
  const maxLevel = levelMap.size > 0 ? Math.max(...levelMap.values()) : 0
  let orphanLevel = maxLevel + 1
  for (const [id] of nodeMap) {
    if (!levelMap.has(id)) {
      levelMap.set(id, orphanLevel++)
    }
  }

  // 按层级分组，计算位置
  const levelGroups = new Map<number, string[]>()
  for (const [id, level] of levelMap) {
    if (!levelGroups.has(level)) levelGroups.set(level, [])
    levelGroups.get(level)!.push(id)
  }

  const startX = 200
  const startY = 200
  const spacingX = 220
  const spacingY = 140

  for (const [level, ids] of levelGroups) {
    const totalHeight = (ids.length - 1) * spacingY
    const baseY = startY - totalHeight / 2

    ids.forEach((id, index) => {
      const nodeInfo = nodeMap.get(id)
      if (!nodeInfo) return

      const size = ELEMENT_SIZES[nodeInfo.type] || { width: 100, height: 80 }
      nodes.push({
        id: nodeInfo.id,
        type: nodeInfo.type,
        x: startX + level * spacingX,
        y: baseY + index * spacingY,
        text: nodeInfo.name,
        properties: nodeInfo.properties,
        width: size.width,
        height: size.height,
      })
    })
  }

  return { nodes, edges }
}

/**
 * 检测 XML 是否为标准 BPMN 2.0（缺少 bpmndi 图形信息）
 */
export function isStandardBpmnXml(xml: string): boolean {
  return !xml.includes('bpmndi:BPMNDiagram') && !xml.includes('BPMNDiagram')
}

export function useBpmnDesigner(containerRef: Ref<HTMLElement | null>) {
  const lf = shallowRef<LogicFlow | null>(null)
  const isReady = ref(false)

  const init = () => {
    if (!containerRef.value) return

    const instance = new LogicFlow({
      container: containerRef.value,
      grid: { size: 20, visible: true, type: 'mesh' },
      keyboard: { enabled: true },
      plugins: [BPMNElements, BpmnXmlAdapter],
      edgeType: 'bpmn:sequenceFlow',
    })

    lf.value = markRaw(instance)
    isReady.value = true

    // 渲染空数据，确保画布完全初始化（生成 canvas-overlay 等元素）
    instance.renderRawData({ nodes: [], edges: [] })
  }

  const render = (xml: string) => {
    if (!lf.value) return

    if (isStandardBpmnXml(xml)) {
      // 标准 BPMN XML：手动解析并自动布局
      const graphData = parseStandardBpmnXml(xml)
      ;(lf.value as any).renderRawData(graphData)
    } else {
      // LogicFlow 格式 XML：使用适配器
      ;(lf.value as any).render(xml)
    }
  }

  const getGraphData = () => {
    return lf.value?.getGraphData()
  }

  const getXmlData = () => {
    const xml = lf.value?.getGraphData() as unknown as string
    if (!xml || typeof xml !== 'string') return xml

    let result = xml
    // 添加 Flowable 命名空间声明（如果 XML 中使用了 flowable: 前缀）
    if (result.includes('flowable:') && !result.includes('xmlns:flowable')) {
      result = result.replace(
        /targetNamespace="[^"]*"/,
        'targetNamespace="http://logic-flow.org"\n         xmlns:flowable="http://flowable.org/bpmn"'
      )
    }
    // 确保每个 bpmn:userTask 都有 xmlns:flowable 命名空间声明（用于候选人策略）
    if (result.includes('candidateStrategy') || result.includes('candidateParam')) {
      if (!result.includes('xmlns:flowable')) {
        result = result.replace(
          /targetNamespace="[^"]*"/,
          'targetNamespace="http://logic-flow.org"\n         xmlns:flowable="http://flowable.org/bpmn"'
        )
      }
    }
    // 清理 BPMN 流程元素上的 width/height 属性（Flowable 校验不允许），
    // 但保留 dc:Bounds 上的 width/height（图形坐标需要）
    // 同时清理 isDefaultFlow 等非标准 BPMN 属性
    result = result.split('\n').map(line => {
      if (/<bpmn:\w+/.test(line)) {
        let cleaned = line
          .replace(/\s+width="[^"]*"/g, '')
          .replace(/\s+height="[^"]*"/g, '')
          .replace(/\s+isDefaultFlow="[^"]*"/g, '')
        // 将 candidateStrategy/candidateParam 转换为 flowable: 前缀属性
        if (/<bpmn:userTask/.test(cleaned)) {
          cleaned = cleaned
            .replace(/\s+candidateStrategy="([^"]*)"/g, ' flowable:candidateStrategy="$1"')
            .replace(/\s+candidateParam="([^"]*)"/g, ' flowable:candidateParam="$1"')
        }
        return cleaned
      }
      return line
    }).join('\n')
    return result
  }

  const clear = () => {
    lf.value?.clearData()
  }

  const undo = () => lf.value?.undo()
  const redo = () => lf.value?.redo()

  const zoom = (size: number) => lf.value?.zoom(size)
  const resetZoom = () => lf.value?.resetZoom()

  const destroy = () => {
    lf.value?.destroy()
    lf.value = null
    isReady.value = false
  }

  return {
    lf,
    isReady,
    init,
    render,
    getGraphData,
    getXmlData,
    clear,
    undo,
    redo,
    zoom,
    resetZoom,
    destroy,
  }
}

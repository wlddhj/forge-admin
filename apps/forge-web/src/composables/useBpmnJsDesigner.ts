import { ref, shallowRef, markRaw, type Ref } from 'vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { BpmnPropertiesPanelModule } from 'bpmn-js-properties-panel'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

/**
 * bpmn-js 设计器 Hook（带属性面板）
 */
export function useBpmnJsDesigner(
  containerRef: Ref<HTMLElement | null>,
  propertiesPanelRef?: Ref<HTMLElement | null>
) {
  const modeler = shallowRef<BpmnModeler | null>(null)
  const isReady = ref(false)

  const init = () => {
    if (!containerRef.value) return

    // 创建带属性面板的 Modeler
    const instance = new BpmnModeler({
      container: containerRef.value,
      propertiesPanel: propertiesPanelRef?.value ? {
        parent: propertiesPanelRef.value,
      } : undefined,
      additionalModules: [
        // 属性面板模块
        BpmnPropertiesPanelModule,
      ],
    })

    modeler.value = markRaw(instance)
    isReady.value = true
  }

  const render = async (xml: string) => {
    if (!modeler.value) return

    try {
      await modeler.value.importXML(xml)
      // 自动调整画布大小以适应内容
      const canvas = modeler.value.get('canvas')
      canvas.zoom('fit-viewport')
    } catch (err) {
      console.error('导入 BPMN XML 失败:', err)
      throw err
    }
  }

  const getXmlData = async (): Promise<string | null> => {
    if (!modeler.value) return null

    try {
      const { xml } = await modeler.value.saveXML({ format: true })
      return xml || null
    } catch (err) {
      console.error('导出 BPMN XML 失败:', err)
      return null
    }
  }

  const clear = () => {
    if (!modeler.value) return
    modeler.value.clear()
  }

  const zoomIn = () => {
    if (!modeler.value) return
    const canvas = modeler.value.get('canvas')
    canvas.zoom(canvas.zoom() * 1.1)
  }

  const zoomOut = () => {
    if (!modeler.value) return
    const canvas = modeler.value.get('canvas')
    canvas.zoom(canvas.zoom() * 0.9)
  }

  const resetZoom = () => {
    if (!modeler.value) return
    const canvas = modeler.value.get('canvas')
    canvas.zoom('fit-viewport')
  }

  const destroy = () => {
    if (modeler.value) {
      modeler.value.destroy()
      modeler.value = null
      isReady.value = false
    }
  }

  /**
   * 获取选中的元素
   */
  const getSelectedElement = () => {
    if (!modeler.value) return null
    const selection = modeler.value.get('selection')
    return selection.get() || null
  }

  /**
   * 更新元素属性
   */
  const updateProperties = (element: any, properties: Record<string, any>) => {
    if (!modeler.value || !element) return
    const modeling = modeler.value.get('modeling')
    modeling.updateProperties(element, properties)
  }

  return {
    modeler,
    isReady,
    init,
    render,
    getXmlData,
    clear,
    zoomIn,
    zoomOut,
    resetZoom,
    destroy,
    getSelectedElement,
    updateProperties,
  }
}

/**
 * 创建初始 BPMN XML（包含开始事件和结束事件）
 */
export function createInitialBpmnXml(processKey: string, processName: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:modeler="http://camunda.org/schema/modeler/1.0"
                  id="Definitions_1"
                  targetNamespace="http://flowable.org/bpmn"
                  exporter="bpmn-js"
                  exporterVersion="18.16.1">
  <bpmn:process id="${processKey}" name="${processName}" isExecutable="true">
    <bpmn:startEvent id="startEvent" name="开始"/>
    <bpmn:endEvent id="endEvent" name="结束"/>
    <bpmn:sequenceFlow id="flow_start_end" sourceRef="startEvent" targetRef="endEvent"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="startEvent_di" bpmnElement="startEvent">
        <dc:Bounds x="180" y="160" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="186" y="202" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="endEvent_di" bpmnElement="endEvent">
        <dc:Bounds x="420" y="160" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="426" y="202" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_start_end_di" bpmnElement="flow_start_end">
        <di:waypoint x="216" y="178"/>
        <di:waypoint x="420" y="178"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}
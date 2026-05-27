// BPMN Modeler 全局类型声明
declare global {
  interface Window {
    bpmnModeler: import('bpmn-js/lib/Modeler').default | null
  }
}

export {}
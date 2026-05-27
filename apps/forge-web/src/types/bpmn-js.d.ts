// bpmn-js 类型声明
declare module 'bpmn-js/lib/Modeler' {
  import type { Diagram } from 'diagram-js/lib/Diagram'

  interface BpmnModelerOptions {
    container?: string | HTMLElement
    width?: string | number
    height?: string | number
    moddleExtensions?: Record<string, any>
    additionalModules?: any[]
    keyboard?: any
    propertiesPanel?: {
      parent: string | HTMLElement
    }
  }

  interface CommandStack {
    execute: (action: string, context: any) => void
    undo: () => void
    redo: () => void
    canUndo: () => boolean
    canRedo: () => boolean
    clear: () => void
    getStack: () => any[]
    getUndoStack: () => any[]
    getRedoStack: () => any[]
  }

  interface Canvas {
    zoom: (level?: number | 'fit-viewport') => number
    getZoom: () => number
    scroll: (delta: { dx: number; dy: number }) => void
    scrollTo: (position: { x: number; y: number }) => void
    getContainer: () => HTMLElement
    getSize: () => { width: number; height: number }
    getAbsoluteBBox: (element: any) => { x: number; y: number; width: number; height: number }
    addMarker: (elementId: string, marker: string) => void
    removeMarker: (elementId: string, marker: string) => void
    hasMarker: (elementId: string, marker: string) => boolean
    findView: (element: any) => any
  }

  interface Modeling {
    createShape: (attrs: any, position: any, target: any, hints?: any) => any
    createConnection: (source: any, target: any, attrs: any, parent: any) => any
    createLabel: (attrs: any, position: any, parent: any) => any
    createParticipant: (attrs: any, process: any, position: any) => any
    moveShape: (shape: any, delta: any, newParent?: any, hints?: any) => void
    moveConnection: (connection: any, delta: any, newParent?: any) => void
    moveElements: (shapes: any[], delta: any, newParent?: any) => void
    resizeShape: (shape: any, newBounds: any, hints?: any) => void
    createSpace: (delta: any, elements: any[], newParent?: any) => void
    removeShape: (shape: any) => void
    removeConnection: (connection: any) => void
    removeElements: (elements: any[]) => void
    alignElements: (elements: any[], alignment: string) => void
    distributeElements: (elements: any[], direction: string) => void
    updateAttachment: (shape: any, host?: any) => void
    updateModdleProperties: (element: any, moddleElement: any, properties: any) => void
    updateProperties: (element: any, properties: any) => void
    claimId: (id: string, moddleElement: any) => void
    unclaimId: (id: string, moddleElement: any) => void
    setColor: (elements: any[], colors: any) => void
    replaceShape: (oldShape: any, newShapeData: any, hints?: any) => any
    toggleCollapseExpansion: (shape: any) => void
    setConnectionWaypoints: (connection: any, waypoints: any) => void
    layoutConnection: (connection: any, hints?: any) => any
    updateLane: (lane: any, newBounds: any) => void
    addLane: (process: any, position: any) => any
    resizeLane: (lane: any, newBounds: any, hints?: any) => void
    splitLane: (lane: any, count: number) => void
    makeLane: (shape: any, process: any) => any
    collapseLane: (lane: any) => void
  }

  interface ElementRegistry {
    get: (id: string) => any
    getAll: () => any[]
    forEach: (callback: (element: any) => void) => void
    filter: (predicate: (element: any) => boolean) => any[]
    find: (predicate: (element: any) => boolean) => any | undefined
    updateGraphics: (element: any, gfx?: any) => void
    add: (element: any, gfx?: any) => void
    remove: (element: any) => void
  }

  interface EventBus {
    on: (event: string | string[], callback: (event: any) => void) => void
    on: (event: string | string[], priority: number, callback: (event: any) => void) => void
    once: (event: string | string[], callback: (event: any) => void) => void
    off: (event: string | string[], callback?: (event: any) => void) => void
    fire: (event: string | string[], payload?: any) => any
  }

  interface Injector {
    get: <T = any>(name: string, strict?: boolean) => T
  }

  interface Moddle {
    create: <T = any>(type: string, attrs?: any) => T
    getType: (nameOrUri: string) => any
    getPackage: (nameOrUri: string) => any
    getPropertyDescriptor: (element: any, name: string) => any
  }

  interface Selection {
    select: (elements: any | any[], add?: boolean) => void
    deselect: (elements?: any | any[]) => void
    get: () => any[]
    isEmpty: () => boolean
    add: (elements: any[]) => void
    remove: (elements: any[]) => void
    toggle: (elements: any[]) => void
    contains: (element: any) => boolean
    clear: () => void
  }

  interface Overlays {
    add: (elementId: string, type: string, overlay: any) => string
    remove: (filter: { element?: string; type?: string; id?: string }) => void
    show: () => void
    hide: () => void
    clear: () => void
    get: (elementId: string, type?: string) => any[]
  }

  class BpmnModeler extends Diagram {
    constructor(options?: BpmnModelerOptions)

    get: <T = any>(serviceName: string) => T

    importXML: (xml: string) => Promise<{ warnings: string[] }>
    importDefinitions: (definitions: any, diagram: any) => Promise<void>
    saveXML: (options?: { format?: boolean; preamble?: boolean }) => Promise<{ xml: string }>
    saveSVG: () => Promise<{ svg: string }>

    getDefinitions: () => any
    getElementRegistry: () => ElementRegistry
    getCanvas: () => Canvas
    getCommandStack: () => CommandStack
    getModeling: () => Modeling
    getSelection: () => Selection
    getOverlays: () => Overlays
    getInjector: () => Injector
    getModdle: () => Moddle
    getEventBus: () => EventBus

    attachTo: (container: string | HTMLElement) => void
    detach: () => void
    destroy: () => void
    clear: () => void
    on: (event: string | string[], callback: (event: any) => void) => void
    off: (event: string | string[], callback?: (event: any) => void) => void
  }

  export default BpmnModeler
}

// bpmn-js/lib/util/ModelUtil 类型声明
declare module 'bpmn-js/lib/util/ModelUtil' {
  export function is(element: any, type: string): boolean
  export function getBusinessObject(element: any): any
}

// bpmn-js/lib/NavigatedViewer 类型声明
declare module 'bpmn-js/lib/NavigatedViewer' {
  interface BpmnViewerOptions {
    container?: string | HTMLElement
    width?: string | number
    height?: string | number
    moddleExtensions?: Record<string, any>
    additionalModules?: any[]
  }

  interface Canvas {
    zoom: (level?: number | 'fit-viewport') => number
    getZoom: () => number
    scroll: (delta: { dx: number; dy: number }) => void
    scrollTo: (position: { x: number; y: number }) => void
    getContainer: () => HTMLElement
    getSize: () => { width: number; height: number }
  }

  class BpmnViewer {
    constructor(options?: BpmnViewerOptions)

    get: <T = any>(serviceName: string) => T

    importXML: (xml: string) => Promise<{ warnings: string[] }>
    saveXML: (options?: { format?: boolean; preamble?: boolean }) => Promise<{ xml: string }>
    saveSVG: () => Promise<{ svg: string }>

    destroy: () => void
    on: (event: string | string[], callback: (event: any) => void) => void
    off: (event: string | string[], callback?: (event: any) => void) => void
  }

  export default BpmnViewer
}
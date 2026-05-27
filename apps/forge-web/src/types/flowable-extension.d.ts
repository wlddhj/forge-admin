// FlowableExtension 模块类型声明
declare module '@/views/workflow/process/bpmn-extension/FlowableExtension.js' {
  import type { ModuleDefinition } from 'diagram-js/lib/Diagram'

  export interface FlowableModdle {
    name: string
    uri: string
    prefix: string
    xml: {
      tagAlias: string
    }
    associations: any[]
    types: any[]
    enumerations: any[]
  }

  export const flowableExtensionModule: ModuleDefinition
  export const flowableModdle: FlowableModdle

  export default function FlowablePropertiesProvider(
    propertiesPanel: any,
    injector: any
  ): void
}
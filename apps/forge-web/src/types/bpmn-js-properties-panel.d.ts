// bpmn-js-properties-panel 类型声明
declare module 'bpmn-js-properties-panel' {
  import type { ModuleDefinition } from 'diagram-js/lib/Diagram'

  export const BpmnPropertiesPanelModule: ModuleDefinition
  export const BpmnPropertiesProviderModule: ModuleDefinition
  export const ZeebePropertiesProviderModule: ModuleDefinition
  export const CamundaPlatformPropertiesProviderModule: ModuleDefinition
}

// @bpmn-io/properties-panel 类型声明
declare module '@bpmn-io/properties-panel' {
  import type { ComponentType } from 'preact'

  export interface TextFieldEntryProps {
    id: string
    label: string
    description?: string
    getValue: () => string
    setValue: (value: string) => void
    debounce?: boolean
    disabled?: boolean
  }

  export interface SelectEntryProps {
    id: string
    label: string
    description?: string
    getValue: () => string
    setValue: (value: string) => void
    getOptions: () => Array<{ value: string; label: string }>
    disabled?: boolean
  }

  export const TextFieldEntry: ComponentType<TextFieldEntryProps>
  export const SelectEntry: ComponentType<SelectEntryProps>
  export const TextAreaEntry: ComponentType<any>
  export const CheckboxEntry: ComponentType<any>
  export const NumberFieldEntry: ComponentType<any>
  export const ToggleSwitchEntry: ComponentType<any>
  export const ListEntry: ComponentType<any>
  export const Group: ComponentType<any>
  export const CollapsibleEntry: ComponentType<any>

  export const isTextFieldEntryEdited: (element: any) => boolean
  export const isSelectEntryEdited: (element: any) => boolean
  export const isTextAreaEntryEdited: (element: any) => boolean
  export const isCheckboxEntryEdited: (element: any) => boolean
  export const isNumberFieldEntryEdited: (element: any) => boolean
  export const isToggleSwitchEntryEdited: (element: any) => boolean

  export const useDebounce: () => (fn: Function, delay?: number) => Function
  export const useError: () => any
  export const useErrors: () => any
  export const useEvent: () => any
  export const useLayoutState: () => any

  export const PropertiesPanel: ComponentType<any>
  export const FeelPopupModule: any
  export const DebounceInputModule: any
}
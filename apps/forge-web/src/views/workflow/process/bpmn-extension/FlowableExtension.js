/**
 * Flowable 属性扩展模块
 * 为 bpmn-js-properties-panel 添加 Flowable 自定义属性
 */

import { is } from 'bpmn-js/lib/util/ModelUtil'
import { TextFieldEntry, SelectEntry, isTextFieldEntryEdited, isSelectEntryEdited } from '@bpmn-io/properties-panel'

// 导入 Flowable moddle 扩展配置
import flowableModdle from './flowable.json'

/**
 * 候选人策略选项
 */
const CANDIDATE_STRATEGIES = [
  { value: '', label: '<无>' },
  { value: '10', label: '指定角色' },
  { value: '20', label: '部门成员' },
  { value: '21', label: '部门负责人' },
  { value: '30', label: '指定用户' },
  { value: '60', label: '表达式' },
]

/**
 * 自定义属性提供者 - 过滤掉不需要的默认属性组
 */
function CustomPropertiesProvider(propertiesPanel, injector) {
  propertiesPanel.registerProvider(200, this)
  this._injector = injector
}

CustomPropertiesProvider.$inject = ['propertiesPanel', 'injector']

CustomPropertiesProvider.prototype.getGroups = function(element) {
  return function(groups) {
    // 过滤掉 Documentation 组
    return groups.filter(group => group.id !== 'documentation')
  }.bind(this)
}

/**
 * Flowable 属性提供者
 */
function FlowablePropertiesProvider(propertiesPanel, injector) {
  propertiesPanel.registerProvider(100, this)
  this._injector = injector
}

FlowablePropertiesProvider.$inject = ['propertiesPanel', 'injector']

FlowablePropertiesProvider.prototype.getGroups = function(element) {
  return function(groups) {
    // 为 UserTask 添加 Flowable 属性组
    if (is(element, 'bpmn:UserTask')) {
      groups.push(createFlowableGroup(element, this._injector))
    }
    return groups
  }.bind(this)
}

/**
 * 创建 Flowable 属性组
 */
function createFlowableGroup(element, injector) {
  const translate = injector.get('translate')

  return {
    id: 'flowable',
    label: translate('Flowable 属性'),
    entries: [
      // 候选人策略
      {
        id: 'candidateStrategy',
        component: CandidateStrategySelect,
        isEdited: isSelectEntryEdited,
      },
      // 矖略参数
      {
        id: 'candidateParam',
        component: CandidateParamTextField,
        isEdited: isTextFieldEntryEdited,
      },
      // 表单标识
      {
        id: 'formKey',
        component: FormKeyTextField,
        isEdited: isTextFieldEntryEdited,
      },
    ],
  }
}

/**
 * 候选人策略下拉选择组件
 */
function CandidateStrategySelect(props) {
  const { element } = props

  const getValue = () => {
    const bo = element.businessObject
    const strategy = bo.get('flowable:candidateStrategy')
    return strategy !== undefined && strategy !== null ? strategy.toString() : ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    const modeling = modeler.get('modeling')
    const bo = element.businessObject
    modeling.updateModdleProperties(element, bo, {
      'flowable:candidateStrategy': value ? parseInt(value) : undefined
    })
  }

  const getOptions = () => {
    return CANDIDATE_STRATEGIES
  }

  return SelectEntry({
    id: 'candidateStrategy',
    label: '候选人策略',
    getValue,
    setValue,
    getOptions,
  })
}

/**
 * 矖略参数文本框组件
 */
function CandidateParamTextField(props) {
  const { element } = props

  const getValue = () => {
    const bo = element.businessObject
    return bo.get('flowable:candidateParam') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    const modeling = modeler.get('modeling')
    const bo = element.businessObject
    modeling.updateModdleProperties(element, bo, {
      'flowable:candidateParam': value || undefined
    })
  }

  const debounce = (fn) => fn

  return TextFieldEntry({
    id: 'candidateParam',
    label: '策略参数',
    description: '根据策略类型填写参数（如角色ID、部门ID等）',
    getValue,
    setValue,
    debounce,
  })
}

/**
 * 表单标识文本框组件
 */
function FormKeyTextField(props) {
  const { element } = props

  const getValue = () => {
    const bo = element.businessObject
    return bo.get('flowable:formKey') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    const modeling = modeler.get('modeling')
    const bo = element.businessObject
    modeling.updateModdleProperties(element, bo, {
      'flowable:formKey': value || undefined
    })
  }

  const debounce = (fn) => fn

  return TextFieldEntry({
    id: 'formKey',
    label: '表单标识',
    description: '任务关联的表单标识',
    getValue,
    setValue,
    debounce,
  })
}

// 模块定义 - 包含自定义过滤器和 Flowable 属性提供者
export const flowableExtensionModule = {
  __init__: ['customPropertiesProvider', 'flowablePropertiesProvider'],
  customPropertiesProvider: ['type', CustomPropertiesProvider],
  flowablePropertiesProvider: ['type', FlowablePropertiesProvider],
}

// 导出 Flowable moddle 扩展配置，供 Modeler 使用
export { flowableModdle }
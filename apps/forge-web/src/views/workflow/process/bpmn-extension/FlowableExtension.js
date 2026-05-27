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
 * 引用数据缓存 - 在设计器挂载时填充
 */
const referenceData = {
  roles: [],
  departments: [],
  users: [],
}

/**
 * 设置引用数据（由 ProcessDesigner.vue 和 ModelDesigner.vue 调用）
 */
export function setReferenceData(data) {
  if (data.roles) {
    referenceData.roles = data.roles.map(r => ({ value: String(r.id), label: r.roleName }))
  }
  if (data.departments) {
    referenceData.departments = data.departments.map(d => ({ value: String(d.id), label: d.deptName }))
  }
  if (data.users) {
    referenceData.users = data.users.map(u => ({ value: String(u.id), label: u.nickname }))
  }
}

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
    if (is(element, 'bpmn:UserTask')) {
      groups.push(createFlowableGroup(element, this._injector))
    }
    return groups
  }.bind(this)
}

/**
 * 创建 Flowable 属性组 - 根据候选人策略动态构建参数条目
 */
function createFlowableGroup(element, injector) {
  const translate = injector.get('translate')
  const bo = element.businessObject
  const strategy = bo.get('flowable:candidateStrategy')
  const strategyStr = strategy !== undefined && strategy !== null ? strategy.toString() : ''

  const entries = [
    {
      id: 'candidateStrategy',
      component: CandidateStrategySelect,
      isEdited: isSelectEntryEdited,
    },
  ]

  // 根据策略类型动态添加参数条目
  if (strategyStr === '10') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamRoleSelect,
      isEdited: isSelectEntryEdited,
    })
  } else if (strategyStr === '20' || strategyStr === '21') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamDeptSelect,
      isEdited: isSelectEntryEdited,
    })
  } else if (strategyStr === '30') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamUserSelect,
      isEdited: isSelectEntryEdited,
    })
  } else if (strategyStr === '60') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamExpressionField,
      isEdited: isTextFieldEntryEdited,
    })
  }
  // 策略为空时不显示参数字段

  entries.push({
    id: 'formKey',
    component: FormKeyTextField,
    isEdited: isTextFieldEntryEdited,
  })

  return {
    id: 'flowable',
    label: translate('Flowable 属性'),
    entries,
  }
}

// ========== 候选人策略选择器 ==========

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
    const updateProps = {
      'flowable:candidateStrategy': value ? parseInt(value) : undefined,
    }
    // 策略变更时清除旧的参数值
    const currentStrategy = getValue()
    if (value !== currentStrategy) {
      updateProps['flowable:candidateParam'] = undefined
    }
    modeling.updateModdleProperties(element, bo, updateProps)
  }

  return SelectEntry({
    id: 'candidateStrategy',
    label: '候选人策略',
    getValue,
    setValue,
    getOptions: () => CANDIDATE_STRATEGIES,
  })
}

// ========== 策略参数：角色选择 ==========

function CandidateParamRoleSelect(props) {
  const { element } = props

  const getValue = () => {
    return element.businessObject.get('flowable:candidateParam') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      'flowable:candidateParam': value || undefined
    })
  }

  return SelectEntry({
    id: 'candidateParam',
    label: '指定角色',
    getValue,
    setValue,
    getOptions: () => [{ value: '', label: '<无>' }, ...referenceData.roles],
  })
}

// ========== 策略参数：部门选择 ==========

function CandidateParamDeptSelect(props) {
  const { element } = props

  const getValue = () => {
    return element.businessObject.get('flowable:candidateParam') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      'flowable:candidateParam': value || undefined
    })
  }

  return SelectEntry({
    id: 'candidateParam',
    label: '指定部门',
    getValue,
    setValue,
    getOptions: () => [{ value: '', label: '<无>' }, ...referenceData.departments],
  })
}

// ========== 策略参数：用户选择 ==========

function CandidateParamUserSelect(props) {
  const { element } = props

  const getValue = () => {
    return element.businessObject.get('flowable:candidateParam') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      'flowable:candidateParam': value || undefined
    })
  }

  return SelectEntry({
    id: 'candidateParam',
    label: '指定用户',
    getValue,
    setValue,
    getOptions: () => [{ value: '', label: '<无>' }, ...referenceData.users],
  })
}

// ========== 策略参数：表达式输入 ==========

function CandidateParamExpressionField(props) {
  const { element } = props

  const getValue = () => {
    return element.businessObject.get('flowable:candidateParam') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      'flowable:candidateParam': value || undefined
    })
  }

  return TextFieldEntry({
    id: 'candidateParam',
    label: '表达式',
    description: 'Flowable 表达式，如 ${initiator}',
    getValue,
    setValue,
    debounce: (fn) => fn,
  })
}

// ========== 表单标识 ==========

function FormKeyTextField(props) {
  const { element } = props

  const getValue = () => {
    return element.businessObject.get('flowable:formKey') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      'flowable:formKey': value || undefined
    })
  }

  return TextFieldEntry({
    id: 'formKey',
    label: '表单标识',
    description: '任务关联的表单标识',
    getValue,
    setValue,
    debounce: (fn) => fn,
  })
}

// 模块定义
export const flowableExtensionModule = {
  __init__: ['customPropertiesProvider', 'flowablePropertiesProvider'],
  customPropertiesProvider: ['type', CustomPropertiesProvider],
  flowablePropertiesProvider: ['type', FlowablePropertiesProvider],
}

export { flowableModdle }

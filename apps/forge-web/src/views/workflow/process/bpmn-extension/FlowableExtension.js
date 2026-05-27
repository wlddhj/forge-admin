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
  { value: '22', label: '指定岗位' },
  { value: '30', label: '指定用户' },
  { value: '34', label: '审批人自选' },
  { value: '35', label: '发起人自选' },
  { value: '36', label: '发起人自己' },
  { value: '37', label: '发起人部门负责人' },
  { value: '38', label: '连续多级部门负责人' },
  { value: '60', label: '表达式' },
]

/**
 * 引用数据缓存 - 在设计器挂载时填充
 */
const referenceData = {
  roles: [],
  departments: [],
  users: [],
  positions: [],
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
  if (data.positions) {
    referenceData.positions = data.positions.map(p => ({ value: String(p.id), label: p.positionName }))
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
  } else if (strategyStr === '22') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamPositionSelect,
      isEdited: isSelectEntryEdited,
    })
  } else if (strategyStr === '30') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamUserSelect,
      isEdited: isSelectEntryEdited,
    })
  } else if (strategyStr === '38') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamDeptLeaderMultiField,
      isEdited: isTextFieldEntryEdited,
    })
  } else if (strategyStr === '60') {
    entries.push({
      id: 'candidateParam',
      component: CandidateParamExpressionField,
      isEdited: isTextFieldEntryEdited,
    })
  }
  // 34=审批人自选, 35=发起人自选, 36=发起人自己, 37=发起人部门负责人 无需参数

  entries.push({
    id: 'formKey',
    component: FormKeyTextField,
    isEdited: isTextFieldEntryEdited,
  })

  // 审批配置
  entries.push(
    { id: 'approveType', component: ApproveTypeSelect, isEdited: isSelectEntryEdited },
    { id: 'approveMethod', component: ApproveMethodSelect, isEdited: isSelectEntryEdited },
    { id: 'rejectHandlerType', component: RejectHandlerSelect, isEdited: isSelectEntryEdited },
    { id: 'assignEmptyHandler', component: AssignEmptyHandlerSelect, isEdited: isSelectEntryEdited },
    { id: 'assignStartUserHandler', component: AssignStartUserHandlerSelect, isEdited: isSelectEntryEdited },
  )

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

// ========== 策略参数：岗位选择 ==========

function CandidateParamPositionSelect(props) {
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
    label: '指定岗位',
    getValue,
    setValue,
    getOptions: () => [{ value: '', label: '<无>' }, ...referenceData.positions],
  })
}

// ========== 策略参数：连续多级部门负责人 ==========

function CandidateParamDeptLeaderMultiField(props) {
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
    label: '部门与层级',
    description: '格式：部门ID:层级数，如 1:2 表示从部门1向上2级',
    getValue,
    setValue,
    debounce: (fn) => fn,
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

// ========== 审批类型 ==========

const APPROVE_TYPE_OPTIONS = [
  { value: '', label: '<默认>' },
  { value: '1', label: '人工审批' },
  { value: '2', label: '自动通过' },
  { value: '3', label: '自动拒绝' },
]

function ApproveTypeSelect(props) {
  const { element } = props
  return SelectEntry({
    id: 'approveType',
    label: '审批类型',
    getValue: () => {
      const v = element.businessObject.get('flowable:approveType')
      return v !== undefined && v !== null ? v.toString() : ''
    },
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:approveType': value ? parseInt(value) : undefined,
      })
    },
    getOptions: () => APPROVE_TYPE_OPTIONS,
  })
}

// ========== 多人审批方式 ==========

const APPROVE_METHOD_OPTIONS = [
  { value: '', label: '<默认>' },
  { value: '1', label: '随机挑选一人' },
  { value: '2', label: '会签(按通过比例)' },
  { value: '3', label: '或签(一人通过/拒绝)' },
  { value: '4', label: '依次审批' },
]

function ApproveMethodSelect(props) {
  const { element } = props
  return SelectEntry({
    id: 'approveMethod',
    label: '审批方式',
    getValue: () => {
      const v = element.businessObject.get('flowable:approveMethod')
      return v !== undefined && v !== null ? v.toString() : ''
    },
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:approveMethod': value ? parseInt(value) : undefined,
      })
    },
    getOptions: () => APPROVE_METHOD_OPTIONS,
  })
}

// ========== 拒绝处理策略 ==========

const REJECT_HANDLER_OPTIONS = [
  { value: '', label: '<默认>' },
  { value: '1', label: '终止流程' },
  { value: '2', label: '驳回到指定节点' },
]

function RejectHandlerSelect(props) {
  const { element } = props
  return SelectEntry({
    id: 'rejectHandlerType',
    label: '拒绝处理',
    getValue: () => {
      const v = element.businessObject.get('flowable:rejectHandlerType')
      return v !== undefined && v !== null ? v.toString() : ''
    },
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:rejectHandlerType': value ? parseInt(value) : undefined,
      })
    },
    getOptions: () => REJECT_HANDLER_OPTIONS,
  })
}

// ========== 审批人为空处理 ==========

const ASSIGN_EMPTY_HANDLER_OPTIONS = [
  { value: '', label: '<默认>' },
  { value: '1', label: '自动通过' },
  { value: '2', label: '自动拒绝' },
  { value: '3', label: '指定人员审批' },
  { value: '4', label: '转管理员' },
]

function AssignEmptyHandlerSelect(props) {
  const { element } = props
  return SelectEntry({
    id: 'assignEmptyHandler',
    label: '空审批人',
    getValue: () => {
      const v = element.businessObject.get('flowable:assignEmptyHandler')
      return v !== undefined && v !== null ? v.toString() : ''
    },
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:assignEmptyHandler': value ? parseInt(value) : undefined,
      })
    },
    getOptions: () => ASSIGN_EMPTY_HANDLER_OPTIONS,
  })
}

// ========== 与发起人相同处理 ==========

const ASSIGN_START_USER_HANDLER_OPTIONS = [
  { value: '', label: '<默认>' },
  { value: '1', label: '由发起人审批' },
  { value: '2', label: '自动跳过' },
  { value: '3', label: '转部门负责人' },
]

function AssignStartUserHandlerSelect(props) {
  const { element } = props
  return SelectEntry({
    id: 'assignStartUserHandler',
    label: '与发起人相同',
    getValue: () => {
      const v = element.businessObject.get('flowable:assignStartUserHandler')
      return v !== undefined && v !== null ? v.toString() : ''
    },
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:assignStartUserHandler': value ? parseInt(value) : undefined,
      })
    },
    getOptions: () => ASSIGN_START_USER_HANDLER_OPTIONS,
  })
}

// 模块定义
export const flowableExtensionModule = {
  __init__: ['customPropertiesProvider', 'flowablePropertiesProvider'],
  customPropertiesProvider: ['type', CustomPropertiesProvider],
  flowablePropertiesProvider: ['type', FlowablePropertiesProvider],
}

export { flowableModdle }

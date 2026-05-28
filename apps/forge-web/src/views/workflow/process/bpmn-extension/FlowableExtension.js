/**
 * Flowable 属性扩展模块
 * 为 bpmn-js-properties-panel 添加 Flowable 自定义属性
 */

import { is } from 'bpmn-js/lib/util/ModelUtil'
import { TextFieldEntry, SelectEntry, CheckboxEntry, isTextFieldEntryEdited, isSelectEntryEdited, isCheckboxEntryEdited } from '@bpmn-io/properties-panel'

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
    if (is(element, 'bpmn:SequenceFlow')) {
      const source = element.source
      if (source && (is(source, 'bpmn:ExclusiveGateway') || is(source, 'bpmn:InclusiveGateway'))) {
        groups.push(createSequenceFlowConditionGroup(element, this._injector))
      }
    }
    if (is(element, 'bpmn:StartEvent')) {
      groups.push(createStartEventGroup(element, this._injector))
    }
    if (is(element, 'bpmn:ExclusiveGateway') || is(element, 'bpmn:ParallelGateway') || is(element, 'bpmn:InclusiveGateway')) {
      groups.push(createGatewayGroup(element, this._injector))
    }
    if (is(element, 'bpmn:ServiceTask')) {
      groups.push(createServiceTaskGroup(element, this._injector))
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

// ========== SequenceFlow 条件配置 ==========

const CONDITION_TYPE_OPTIONS = [
  { value: '', label: '<无条件>' },
  { value: '${approved == true}', label: '通过 (approved == true)' },
  { value: '${approved == false}', label: '驳回 (approved == false)' },
  { value: 'custom', label: '自定义表达式' },
]

function createSequenceFlowConditionGroup(element, injector) {
  const translate = injector.get('translate')

  const entries = [
    { id: 'conditionType', component: ConditionTypeSelect, isEdited: isSelectEntryEdited },
    { id: 'customCondition', component: CustomConditionField, isEdited: isTextFieldEntryEdited },
  ]

  return {
    id: 'flowable-condition',
    label: translate('条件配置'),
    entries,
  }
}

function ConditionTypeSelect(props) {
  const { element } = props

  const getValue = () => {
    const bo = element.businessObject
    const condition = bo.conditionExpression
    if (!condition) return ''
    const body = condition.body || ''
    if (body === '${approved == true}') return body
    if (body === '${approved == false}') return body
    return 'custom'
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    const modeling = modeler.get('modeling')
    const moddle = modeler.get('moddle')

    if (!value) {
      modeling.updateProperties(element, { conditionExpression: undefined })
    } else if (value !== 'custom') {
      const conditionExpression = moddle.create('bpmn:FormalExpression', { body: value })
      modeling.updateProperties(element, { conditionExpression })
    }
  }

  return SelectEntry({
    id: 'conditionType',
    label: '条件类型',
    getValue,
    setValue,
    getOptions: () => CONDITION_TYPE_OPTIONS,
  })
}

function CustomConditionField(props) {
  const { element } = props

  const getValue = () => {
    const bo = element.businessObject
    const condition = bo.conditionExpression
    if (!condition) return ''
    const body = condition.body || ''
    if (body === '${approved == true}' || body === '${approved == false}') return ''
    return body
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    const modeling = modeler.get('modeling')
    const moddle = modeler.get('moddle')

    if (!value) {
      modeling.updateProperties(element, { conditionExpression: undefined })
    } else {
      const conditionExpression = moddle.create('bpmn:FormalExpression', { body: value })
      modeling.updateProperties(element, { conditionExpression })
    }
  }

  const isPreset = () => {
    const bo = element.businessObject
    const condition = bo.conditionExpression
    if (!condition) return false
    const body = condition.body || ''
    return body === '${approved == true}' || body === '${approved == false}'
  }

  return TextFieldEntry({
    id: 'customCondition',
    label: '自定义表达式',
    description: isPreset() ? '选择了预设条件时不可编辑' : 'Flowable 条件表达式，如 ${amount > 1000}',
    getValue,
    setValue,
    debounce: (fn) => fn,
    disabled: isPreset(),
  })
}

// ========== StartEvent 属性 ==========

function createStartEventGroup(element, injector) {
  const translate = injector.get('translate')
  return {
    id: 'flowable-start',
    label: translate('Flowable 属性'),
    entries: [
      { id: 'initiator', component: InitiatorTextField, isEdited: isTextFieldEntryEdited },
      { id: 'startFormKey', component: StartFormKeyTextField, isEdited: isTextFieldEntryEdited },
    ],
  }
}

function InitiatorTextField(props) {
  const { element } = props

  const getValue = () => {
    return element.businessObject.get('flowable:initiator') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      'flowable:initiator': value || undefined,
    })
  }

  return TextFieldEntry({
    id: 'initiator',
    label: '发起人变量',
    description: '流程发起人存储的变量名，如填 initiator',
    getValue,
    setValue,
    debounce: (fn) => fn,
  })
}

function StartFormKeyTextField(props) {
  const { element } = props

  const getValue = () => {
    return element.businessObject.get('flowable:formKey') || ''
  }

  const setValue = (value) => {
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      'flowable:formKey': value || undefined,
    })
  }

  return TextFieldEntry({
    id: 'startFormKey',
    label: '表单标识',
    description: '开始事件关联的表单标识',
    getValue,
    setValue,
    debounce: (fn) => fn,
  })
}

// ========== Gateway 异步执行配置 ==========

function createGatewayGroup(element, injector) {
  const translate = injector.get('translate')
  return {
    id: 'flowable-gateway',
    label: translate('Flowable 属性'),
    entries: [
      { id: 'asyncBefore', component: AsyncBeforeCheckbox, isEdited: isCheckboxEntryEdited },
      { id: 'asyncAfter', component: AsyncAfterCheckbox, isEdited: isCheckboxEntryEdited },
      { id: 'exclusive', component: ExclusiveCheckbox, isEdited: isCheckboxEntryEdited },
    ],
  }
}

function AsyncBeforeCheckbox(props) {
  const { element } = props
  return CheckboxEntry({
    id: 'asyncBefore',
    label: '异步前',
    getValue: () => !!element.businessObject.get('flowable:asyncBefore'),
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:asyncBefore': value || undefined,
      })
    },
  })
}

function AsyncAfterCheckbox(props) {
  const { element } = props
  return CheckboxEntry({
    id: 'asyncAfter',
    label: '异步后',
    getValue: () => !!element.businessObject.get('flowable:asyncAfter'),
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:asyncAfter': value || undefined,
      })
    },
  })
}

function ExclusiveCheckbox(props) {
  const { element } = props
  return CheckboxEntry({
    id: 'exclusive',
    label: '排他执行',
    description: '异步模式下是否独占执行，默认开启',
    getValue: () => {
      const v = element.businessObject.get('flowable:exclusive')
      return v === undefined ? true : !!v
    },
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
        'flowable:exclusive': value,
      })
    },
  })
}

// ========== ServiceTask 实现配置 ==========

const SERVICE_IMPL_OPTIONS = [
  { value: '', label: '<无>' },
  { value: 'class', label: 'Java 类' },
  { value: 'delegateExpression', label: '委托表达式' },
  { value: 'expression', label: '表达式' },
]

function createServiceTaskGroup(element, injector) {
  const translate = injector.get('translate')

  return {
    id: 'flowable-service',
    label: translate('Flowable 属性'),
    entries: [
      { id: 'serviceImplType', component: ServiceImplTypeSelect, isEdited: isSelectEntryEdited },
      { id: 'serviceImplValue', component: ServiceImplValueField, isEdited: isTextFieldEntryEdited },
    ],
  }
}

function getServiceImplType(element) {
  const bo = element.businessObject
  if (bo.get('flowable:class') !== undefined) return 'class'
  if (bo.get('flowable:delegateExpression') !== undefined) return 'delegateExpression'
  if (bo.get('flowable:expression') !== undefined) return 'expression'
  return ''
}

function ServiceImplTypeSelect(props) {
  const { element } = props

  return SelectEntry({
    id: 'serviceImplType',
    label: '实现方式',
    getValue: () => getServiceImplType(element),
    setValue: (value) => {
      const modeler = window.bpmnModeler
      if (!modeler) return
      const modeling = modeler.get('modeling')
      const bo = element.businessObject
      const updateProps = {
        'flowable:class': undefined,
        'flowable:delegateExpression': undefined,
        'flowable:expression': undefined,
      }
      if (value) {
        updateProps['flowable:' + value] = ''
      }
      modeling.updateModdleProperties(element, bo, updateProps)
    },
    getOptions: () => SERVICE_IMPL_OPTIONS,
  })
}

function ServiceImplValueField(props) {
  const { element } = props
  const implType = getServiceImplType(element)

  const getValue = () => {
    if (!implType) return ''
    const bo = element.businessObject
    const val = bo.get('flowable:' + implType)
    return val !== undefined ? val : ''
  }

  const setValue = (value) => {
    if (!implType) return
    const modeler = window.bpmnModeler
    if (!modeler) return
    modeler.get('modeling').updateModdleProperties(element, element.businessObject, {
      ['flowable:' + implType]: value || undefined,
    })
  }

  const labels = {
    class: 'Java 类全限定名',
    delegateExpression: '委托表达式，如 ${myDelegateBean}',
    expression: '表达式，如 ${myService.execute(execution)}',
  }

  return TextFieldEntry({
    id: 'serviceImplValue',
    label: '实现值',
    description: implType ? labels[implType] : '请先选择实现方式',
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

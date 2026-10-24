/**
 * form-create 工具函数
 * 参考 shi9 的 formCreate 工具函数设计
 */

/** 编码表单配置 */
export function encodeConf(designer: any): string {
  return JSON.stringify(designer.getOption())
}

/** 编码表单字段 */
export function encodeFields(designer: any): string {
  const rule = designer.getJson()
  const rules = typeof rule === 'string' ? JSON.parse(rule) : rule
  return JSON.stringify(rules.map((r: any) => JSON.stringify(r)))
}

/** 解码表单字段 */
export function decodeFields(fields: string): any[] {
  if (!fields) return []
  try {
    const arr = JSON.parse(fields)
    return arr.map((f: string) => typeof f === 'string' ? JSON.parse(f) : f)
  } catch {
    return []
  }
}

/** 布局组件类型列表（这些组件不需要设置 disabled） */
const layoutComponentTypes = [
  'row', 'col', 'div', 'card', 'tab', 'tabPane', 'group', 'span',
  'fcRow', 'fcCol', 'fcDiv', 'fcCard', 'fcTab', 'fcTabPane', 'fcGroup',
  'el-row', 'el-col', 'el-card', 'el-tabs', 'el-tab-pane'
]

/** 递归设置字段禁用状态 */
function setRuleDisabled(rule: any): any {
  if (!rule) return rule

  // 如果有 children，递归处理
  if (rule.children && Array.isArray(rule.children)) {
    rule.children = rule.children.map((child: any) => setRuleDisabled(child))
  }

  // 获取组件类型
  const type = rule.type || ''

  // 如果是布局组件，不设置 disabled，只处理 children
  if (layoutComponentTypes.includes(type)) {
    return rule
  }

  // 对非布局组件设置 disabled
  if (rule.props) {
    rule.props.disabled = true
  } else {
    rule.props = { disabled: true }
  }

  return rule
}

/** 解码表单字段并设置为禁用状态 */
export function decodeFieldsDisabled(fields: string): any[] {
  const rules = decodeFields(fields)
  return rules.map(rule => setRuleDisabled(rule))
}

/** 加载表单配置和字段到 form-create 组件 */
export function setConfAndFields(
  target: any,
  conf: string,
  fields: string,
  value?: Record<string, any>
) {
  if (!target) return
  const decodedFields = decodeFields(fields)
  if (conf) {
    try {
      target.setOption(JSON.parse(conf))
    } catch { /* ignore */ }
  }
  target.setRule(decodedFields)
  if (value) {
    Object.keys(value).forEach(key => {
      target.setValue(key, value[key])
    })
  }
}

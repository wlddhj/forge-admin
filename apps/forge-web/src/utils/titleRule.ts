/**
 * 流程标题规则渲染（与后端 InstanceTitleRenderer 同构，发起弹窗实时预览用）
 *
 * 模板占位符 ${xxx}：内置 processName / initiatorName / businessKey / date(yyyy-MM-dd)，
 * 其余按表单变量名取值；变量缺失替换为空串；渲染结果为空返回 null。
 */
const PLACEHOLDER = /\$\{([^}]+)}/g

export interface TitleRenderContext {
  processName?: string
  initiatorName?: string
  businessKey?: string
  /** 表单变量（发起表单实时填写值） */
  variables?: Record<string, unknown>
}

export function renderTitle(rule: string | undefined, ctx: TitleRenderContext): string | null {
  if (!rule || !rule.trim()) {
    return null
  }
  const resolve = (rawName: string): string => {
    const name = rawName.trim()
    if (name === 'processName') return ctx.processName ?? ''
    if (name === 'initiatorName') return ctx.initiatorName ?? ''
    if (name === 'businessKey') return ctx.businessKey ?? ''
    if (name === 'date') return new Date().toISOString().slice(0, 10)
    const value = ctx.variables?.[name]
    return value == null ? '' : String(value)
  }
  const rendered = rule.replace(PLACEHOLDER, (_, name: string) => resolve(name)).trim()
  return rendered === '' ? null : rendered
}

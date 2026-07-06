import type { JSONSchema7 } from 'json-schema'

/** 大屏主题（与后端 sys_screen.theme 对齐） */
export type ScreenTheme = 'dark-tech' | 'blue-deep' | 'black-gold'

/** 大屏状态（与后端 ScreenStatus 对齐） */
export type ScreenStatus = 0 | 1

/** 单字段定义 */
export interface FieldDef {
  name: string
  type: 'string' | 'number' | 'date' | 'boolean'
  sample?: unknown
}

/** 卡片数据形状契约（前端用于自动字段映射） */
export interface CardDataShape {
  fields: FieldDef[]
  sample: Record<string, unknown>
}

/** 单张卡片（grid item + 组件配置） */
export interface ScreenCard {
  id: string
  type: string
  title?: string
  x: number
  y: number
  w: number
  h: number
  dataSourceId?: number | null
  refresh?: number
  options?: Record<string, unknown>
}

/** 大屏配置（运行/草稿共用） */
export interface ScreenConfig {
  version: number
  theme: ScreenTheme
  cards: ScreenCard[]
}

/** 卡片组件契约 */
export interface ScreenCardComponent<TConfig = Record<string, unknown>, TData = unknown> {
  type: string
  component: import('vue').Component
  meta: {
    title: string
    icon: string
    defaultProps: TConfig
    configSchema: JSONSchema7
    dataShape: CardDataShape
    minWidth: number
    minHeight: number
  }
}

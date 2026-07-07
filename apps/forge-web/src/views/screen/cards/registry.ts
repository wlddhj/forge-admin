import type { ScreenCardComponent } from './types'
import DigitalNumber from './digital-number/index.vue'
import LineChart from './line-chart/index.vue'
import BarChart from './bar-chart/index.vue'
import PieChart from './pie-chart/index.vue'
import ScrollTable from './scroll-table/index.vue'
import TextBoard from './text-board/index.vue'

export interface Registry<T> {
  register(entry: T): void
  get(type: string): T | undefined
  list(): T[]
  has(type: string): boolean
}

export function createRegistry<T extends { type: string }>(): Registry<T> {
  const map = new Map<string, T>()
  return {
    register(entry) {
      if (map.has(entry.type)) throw new Error(`Card type already registered: ${entry.type}`)
      map.set(entry.type, entry)
    },
    get(type) { return map.get(type) },
    list() { return Array.from(map.values()) },
    has(type) { return map.has(type) }
  }
}

export const cardRegistry: Registry<ScreenCardComponent> = createRegistry<ScreenCardComponent>()

const ENTRIES: ScreenCardComponent[] = [
  {
    type: 'digital-number',
    component: DigitalNumber,
    meta: {
      title: '数字翻牌器', icon: 'Discount',
      defaultProps: { title: '指标', unit: '', decimals: 0, duration: 1500, color: 'var(--screen-accent)' },
      configSchema: { type: 'object', properties: {
        title: { type: 'string', title: '标题' },
        valueField: { type: 'string', title: '取值字段' },
        unit: { type: 'string', title: '单位' },
        decimals: { type: 'number', default: 0, minimum: 0, maximum: 6 },
        duration: { type: 'number', default: 1500 }
      }},
      dataShape: { fields: [{ name: 'value', type: 'number' }], sample: { value: 1234 } },
      minWidth: 4, minHeight: 3
    }
  },
  {
    type: 'line-chart',
    component: LineChart,
    meta: {
      title: '折线图', icon: 'TrendCharts',
      defaultProps: { xField: 'x', yField: 'y', seriesField: '', smooth: true },
      configSchema: { type: 'object', properties: {
        xField: { type: 'string', default: 'x' },
        yField: { type: 'string', default: 'y' },
        seriesField: { type: 'string' },
        smooth: { type: 'boolean', default: true }
      }},
      dataShape: { fields: [
        { name: 'x', type: 'date', sample: '2026-07-06' },
        { name: 'y', type: 'number', sample: 100 }
      ], sample: [{ x: '2026-07-01', y: 10 }] },
      minWidth: 6, minHeight: 4
    }
  },
  {
    type: 'bar-chart',
    component: BarChart,
    meta: {
      title: '柱状图', icon: 'DataLine',
      defaultProps: { xField: 'name', yField: 'value' },
      configSchema: { type: 'object', properties: {
        xField: { type: 'string', default: 'name' },
        yField: { type: 'string', default: 'value' }
      }},
      dataShape: { fields: [
        { name: 'name', type: 'string' }, { name: 'value', type: 'number' }
      ], sample: [{ name: 'A', value: 10 }] },
      minWidth: 6, minHeight: 4
    }
  },
  {
    type: 'pie-chart',
    component: PieChart,
    meta: {
      title: '饼图', icon: 'PieChart',
      defaultProps: { nameField: 'name', valueField: 'value' },
      configSchema: { type: 'object', properties: {
        nameField: { type: 'string', default: 'name' },
        valueField: { type: 'string', default: 'value' }
      }},
      dataShape: { fields: [
        { name: 'name', type: 'string' }, { name: 'value', type: 'number' }
      ], sample: [{ name: 'A', value: 30 }] },
      minWidth: 6, minHeight: 6
    }
  },
  {
    type: 'scroll-table',
    component: ScrollTable,
    meta: {
      title: '滚动列表', icon: 'List',
      defaultProps: { rowCount: 10, sortField: '' },
      configSchema: { type: 'object', properties: {
        rowCount: { type: 'number', default: 10, minimum: 3, maximum: 50 },
        sortField: { type: 'string' }
      }},
      dataShape: { fields: [], sample: {} },
      minWidth: 6, minHeight: 4
    }
  },
  {
    type: 'text-board',
    component: TextBoard,
    meta: {
      title: '文字看板', icon: 'Document',
      defaultProps: { template: '{{value}}', fontSize: 32 },
      configSchema: { type: 'object', properties: {
        template: { type: 'string', default: '{{value}}' },
        fontSize: { type: 'number', default: 32, minimum: 12, maximum: 96 }
      }},
      dataShape: { fields: [{ name: 'value', type: 'string' }], sample: { value: '运行正常' } },
      minWidth: 4, minHeight: 3
    }
  }
]

export function registerBuiltinCards(): void {
  for (const entry of ENTRIES) {
    if (cardRegistry.has(entry.type)) continue
    cardRegistry.register(entry)
  }
}
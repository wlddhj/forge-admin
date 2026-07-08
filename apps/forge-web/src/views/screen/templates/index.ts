import type { ScreenConfig, ScreenCard } from '@/types/screen'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'

const cid = (): string => `card-${Math.random().toString(36).slice(2, 10)}`

const baseCard = (overrides: Partial<ScreenCard>): ScreenCard => ({
  id: cid(),
  type: 'digital-number',
  x: 0, y: 0, w: 6, h: 4,
  dataSourceId: null,
  refresh: 30,
  options: {},
  ...overrides
})

export const presetTemplates: { code: string; name: string; description: string; config: ScreenConfig }[] = [
  {
    code: 'blank', name: '空白', description: '仅画布，无卡片',
    config: { version: 1, theme: SCREEN_DEFAULT_THEME, cards: [] }
  },
  {
    code: 'hero-3', name: '1 大 3 小', description: '核心指标 + 辅助图表',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'digital-number', x: 0, y: 0, w: 12, h: 10 }),
        baseCard({ type: 'line-chart', x: 12, y: 0, w: 12, h: 3 }),
        baseCard({ type: 'bar-chart', x: 12, y: 3, w: 12, h: 3 }),
        baseCard({ type: 'pie-chart', x: 12, y: 6, w: 12, h: 4 })
      ]
    }
  },
  {
    code: 'quad', name: '4 宫格', description: '等量指标对比',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'digital-number', x: 0, y: 0, w: 12, h: 5 }),
        baseCard({ type: 'digital-number', x: 12, y: 0, w: 12, h: 5 }),
        baseCard({ type: 'digital-number', x: 0, y: 5, w: 12, h: 5 }),
        baseCard({ type: 'digital-number', x: 12, y: 5, w: 12, h: 5 })
      ]
    }
  },
  {
    code: 'top-bottom', name: '上下分栏', description: 'KPI + 明细',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'digital-number', x: 0, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'digital-number', x: 6, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'digital-number', x: 12, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'digital-number', x: 18, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'line-chart', x: 0, y: 2, w: 12, h: 8 }),
        baseCard({ type: 'bar-chart', x: 12, y: 2, w: 12, h: 8 })
      ]
    }
  },
  {
    code: 'triple', name: '三栏', description: '多维监控',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'line-chart', x: 0, y: 0, w: 8, h: 10 }),
        baseCard({ type: 'map-chart', x: 8, y: 0, w: 8, h: 10 }),
        baseCard({ type: 'scroll-table', x: 16, y: 0, w: 8, h: 10 })
      ]
    }
  },
  {
    code: 'presentation', name: '大屏汇报', description: '领导汇报标准模板',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'text-board', x: 0, y: 0, w: 24, h: 2,
          options: { template: '领导驾驶舱', fontSize: 48 } }),
        baseCard({ type: 'digital-number', x: 0, y: 2, w: 8, h: 4 }),
        baseCard({ type: 'digital-number', x: 8, y: 2, w: 8, h: 4 }),
        baseCard({ type: 'digital-number', x: 16, y: 2, w: 8, h: 4 }),
        baseCard({ type: 'line-chart', x: 0, y: 6, w: 12, h: 8 }),
        baseCard({ type: 'pie-chart', x: 12, y: 6, w: 12, h: 8 })
      ]
    }
  }
]

export function getTemplate(code: string) {
  return presetTemplates.find(t => t.code === code) ?? presetTemplates[0]
}

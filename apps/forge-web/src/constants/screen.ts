import type { ScreenTheme } from '@/types/screen'

export const SCREEN_BASE_WIDTH = 1920
export const SCREEN_BASE_HEIGHT = 1080

export const SCREEN_GRID_COLUMNS = 24
export const SCREEN_MAX_CARD_ROWS = 24

export const SCREEN_REFRESH_OPTIONS: { label: string; value: number }[] = [
  { label: '不刷新', value: 0 },
  { label: '10 秒', value: 10 },
  { label: '30 秒', value: 30 },
  { label: '1 分钟', value: 60 },
  { label: '5 分钟', value: 300 }
]

export const SCREEN_THEMES: { value: ScreenTheme; label: string }[] = [
  { value: 'dark-tech', label: '暗色科技' },
  { value: 'blue-deep', label: '深空蓝' },
  { value: 'black-gold', label: '黑金' }
]

export const SCREEN_DEFAULT_THEME: ScreenTheme = 'dark-tech'

export const DATA_SOURCE_TYPE_HTTP = 'HTTP'
export const DATA_SOURCE_TYPE_SQL = 'SQL'

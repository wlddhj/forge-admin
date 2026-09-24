export type Palette = 'blue' | 'purple' | 'green' | 'crimson' | 'orange' | 'cyan' | 'teal' | 'custom'
export type LayoutKind = 'sidebar' | 'top'
export type StyleKind = 'flat' | 'glass' | 'card' | 'compact'
export type ThemeMode = 'light' | 'dark' | 'auto'

export interface Preset {
  id: string
  name: string
  palette: Palette
  layout: LayoutKind
  style: StyleKind
}

export const PRESETS: Preset[] = [
  { id: 'default',  name: '默认',     palette: 'blue',    layout: 'sidebar', style: 'flat' },
  { id: 'geek',     name: '极客紫',   palette: 'purple',  layout: 'top',     style: 'glass' },
  { id: 'business', name: '商务器',   palette: 'green',   layout: 'sidebar', style: 'card' },
  { id: 'dark-pro', name: '酷暗黑',   palette: 'crimson', layout: 'sidebar', style: 'compact' },
  { id: 'sunset',   name: '日暮橙',   palette: 'orange',  layout: 'sidebar', style: 'flat' },
  { id: 'amber',    name: '琥珀琉璃', palette: 'orange',  layout: 'top',     style: 'glass' },
  { id: 'ocean',    name: '海洋青',   palette: 'cyan',    layout: 'sidebar', style: 'flat' },
  { id: 'mint',     name: '薄荷青',   palette: 'cyan',    layout: 'top',     style: 'card' },
  { id: 'island',   name: '青碧岛',   palette: 'teal',    layout: 'sidebar', style: 'glass' },
  { id: 'forest',   name: '青碧紧凑', palette: 'teal',    layout: 'sidebar', style: 'compact' }
]

export const getPreset = (id: string): Preset =>
  PRESETS.find(p => p.id === id) ?? PRESETS[0]

/** 系统默认主题（后端 sys_config sys.brand.theme.* 可配置；此为前端回退默认值，须与后端 BrandTheme.DEFAULT_* 一致） */
export interface DefaultThemeConfig {
  palette: Palette
  layout: LayoutKind
  style: StyleKind
  mode: ThemeMode
}

export const DEFAULT_THEME: DefaultThemeConfig = {
  palette: 'blue',
  layout: 'sidebar',
  style: 'flat',
  mode: 'auto'
}

/** EP 默认主色（custom 模式首次默认值） */
export const DEFAULT_CUSTOM_PRIMARY = '#409EFF'

/** HEX 主色校验（严格 6 位 #RRGGBB） */
export function isValidPrimary(hex: string): boolean {
  return /^#[0-9a-f]{6}$/i.test(hex.trim())
}

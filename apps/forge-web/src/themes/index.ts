export type Palette = 'blue' | 'purple' | 'green' | 'crimson'
export type LayoutKind = 'sidebar' | 'top'
export type StyleKind = 'flat' | 'glass' | 'card' | 'compact'

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
  { id: 'dark-pro', name: '酷暗黑',   palette: 'crimson', layout: 'sidebar', style: 'compact' }
]

export const getPreset = (id: string): Preset =>
  PRESETS.find(p => p.id === id) ?? PRESETS[0]

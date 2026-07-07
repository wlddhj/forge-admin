import type { ScreenTheme } from '@/types/screen'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'
import { usePageConfigStore } from '@/stores/pageConfig'

/** palette → 主色 HEX 映射（与 themes/index.ts 的 PRESETS 对齐） */
const PALETTE_PRIMARY: Record<string, string> = {
  blue: '#409EFF',
  purple: '#7C4DFF',
  green: '#52C41A',
  crimson: '#F5222D',
  custom: '#409EFF'
}

export function applyScreenTheme(theme: ScreenTheme): void {
  const root = document.documentElement
  root.setAttribute('data-screen-theme', theme)

  const pageConfig = usePageConfigStore()
  const palette = pageConfig.config.value.palette
  const primary = palette === 'custom'
    ? (pageConfig.config.value.customPrimary || PALETTE_PRIMARY.custom)
    : PALETTE_PRIMARY[palette] || PALETTE_PRIMARY.blue
  root.style.setProperty('--screen-accent', primary)
}

export function getCurrentScreenTheme(): ScreenTheme {
  const attr = document.documentElement.getAttribute('data-screen-theme') as ScreenTheme | null
  return attr ?? SCREEN_DEFAULT_THEME
}
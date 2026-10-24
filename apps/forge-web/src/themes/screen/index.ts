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

  let primary = PALETTE_PRIMARY.blue
  try {
    const pageConfig = usePageConfigStore()
    // 历史误用修复:config 是普通对象非 ref,曾取 ?.value 恒 undefined 致自定义主题从未生效
    const cfg = pageConfig.config
    const palette = cfg?.palette
    if (palette === 'custom') {
      primary = cfg?.customPrimary || PALETTE_PRIMARY.custom
    } else if (palette && palette in PALETTE_PRIMARY) {
      primary = PALETTE_PRIMARY[palette]
    }
  } catch (e) {
    console.warn('[applyScreenTheme] pageConfig not available, use default blue', e)
  }
  root.style.setProperty('--screen-accent', primary)
}

export function getCurrentScreenTheme(): ScreenTheme {
  const attr = document.documentElement.getAttribute('data-screen-theme') as ScreenTheme | null
  return attr ?? SCREEN_DEFAULT_THEME
}
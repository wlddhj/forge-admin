import { describe, it, expect } from 'vitest'
import { PRESETS, getPreset } from '@/themes'
import type { Palette, LayoutKind, StyleKind, Preset } from '@/themes'

describe('套餐注册表', () => {
  it('注册了 4 个套餐', () => {
    expect(PRESETS).toHaveLength(4)
  })

  it('套餐 id 唯一', () => {
    const ids = PRESETS.map(p => p.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('default 套餐三维度组合正确', () => {
    expect(getPreset('default')).toMatchObject({
      palette: 'blue',
      layout: 'sidebar',
      style: 'flat'
    })
  })

  it('geek 套餐三维度组合正确', () => {
    expect(getPreset('geek')).toMatchObject({
      palette: 'purple',
      layout: 'top',
      style: 'glass'
    })
  })

  it('business 套餐三维度组合正确', () => {
    expect(getPreset('business')).toMatchObject({
      palette: 'green',
      layout: 'sidebar',
      style: 'card'
    })
  })

  it('dark-pro 套餐三维度组合正确', () => {
    expect(getPreset('dark-pro')).toMatchObject({
      palette: 'crimson',
      layout: 'sidebar',
      style: 'compact'
    })
  })

  it('未知 id 回落到 default', () => {
    expect(getPreset('unknown').id).toBe('default')
    expect(getPreset('').id).toBe('default')
  })

  it('类型导出可用', () => {
    const p: Palette = 'blue'
    const l: LayoutKind = 'sidebar'
    const s: StyleKind = 'flat'
    const preset: Preset = { id: 'x', name: 'X', palette: p, layout: l, style: s }
    expect(preset).toBeDefined()
  })
})

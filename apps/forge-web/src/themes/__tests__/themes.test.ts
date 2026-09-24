import { describe, it, expect } from 'vitest'
import { PRESETS, getPreset, DEFAULT_THEME } from '@/themes'
import type { Palette, LayoutKind, StyleKind, Preset } from '@/themes'

describe('套餐注册表', () => {
  it('注册了 10 个套餐', () => {
    expect(PRESETS).toHaveLength(10)
  })

  it('套餐 id 唯一', () => {
    const ids = PRESETS.map(p => p.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('每个调色板至少有一个套餐', () => {
    const usedPalettes = new Set(PRESETS.map(p => p.palette))
    ;(['blue', 'purple', 'green', 'crimson', 'orange', 'cyan', 'teal'] as Palette[]).forEach(palette => {
      expect(usedPalettes.has(palette)).toBe(true)
    })
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

  it('sunset 套餐三维度组合正确', () => {
    expect(getPreset('sunset')).toMatchObject({
      palette: 'orange',
      layout: 'sidebar',
      style: 'flat'
    })
  })

  it('amber 套餐三维度组合正确', () => {
    expect(getPreset('amber')).toMatchObject({
      palette: 'orange',
      layout: 'top',
      style: 'glass'
    })
  })

  it('ocean 套餐三维度组合正确', () => {
    expect(getPreset('ocean')).toMatchObject({
      palette: 'cyan',
      layout: 'sidebar',
      style: 'flat'
    })
  })

  it('mint 套餐三维度组合正确', () => {
    expect(getPreset('mint')).toMatchObject({
      palette: 'cyan',
      layout: 'top',
      style: 'card'
    })
  })

  it('island 套餐三维度组合正确', () => {
    expect(getPreset('island')).toMatchObject({
      palette: 'teal',
      layout: 'sidebar',
      style: 'glass'
    })
  })

  it('forest 套餐三维度组合正确', () => {
    expect(getPreset('forest')).toMatchObject({
      palette: 'teal',
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

describe('系统默认主题', () => {
  it('DEFAULT_THEME 与后端回退值一致', () => {
    expect(DEFAULT_THEME).toEqual({
      palette: 'blue',
      layout: 'sidebar',
      style: 'flat',
      mode: 'auto'
    })
  })

  it('DEFAULT_THEME palette 不含 custom', () => {
    expect(DEFAULT_THEME.palette).not.toBe('custom')
  })
})

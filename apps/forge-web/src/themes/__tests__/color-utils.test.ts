import { describe, it, expect } from 'vitest'
import { hexToRgb, rgbToHex, mix, lightStep, darkStep, isValidHex } from '@/themes/color-utils'

describe('color-utils', () => {
  describe('hexToRgb', () => {
    it('解析 6 位 HEX（带 #）', () => {
      expect(hexToRgb('#409EFF')).toEqual({ r: 64, g: 158, b: 255 })
    })

    it('解析 6 位 HEX（不带 #）', () => {
      expect(hexToRgb('409EFF')).toEqual({ r: 64, g: 158, b: 255 })
    })

    it('解析失败返回 null', () => {
      expect(hexToRgb('xyz')).toBeNull()
      expect(hexToRgb('#abc')).toBeNull()  // 3 位简写不支持
    })
  })

  describe('rgbToHex', () => {
    it('RGB 转 HEX（小写带 #）', () => {
      expect(rgbToHex({ r: 64, g: 158, b: 255 })).toBe('#409eff')
    })

    it('clamp 越界值', () => {
      expect(rgbToHex({ r: -10, g: 300, b: 128 })).toBe('#00ff80')
    })
  })

  describe('mix', () => {
    it('权重 100 返回 c1', () => {
      expect(mix('#ff0000', '#00ff00', 100)).toBe('#ff0000')
    })

    it('权重 0 返回 c2', () => {
      expect(mix('#ff0000', '#00ff00', 0)).toBe('#00ff00')
    })

    it('权重 50 平均混合', () => {
      // (255+0)/2 = 127.5 → Math.round = 128 = 0x80
      expect(mix('#ff0000', '#000000', 50)).toBe('#800000')
    })

    it('解析失败回落 c1', () => {
      expect(mix('invalid', '#ffffff', 50)).toBe('invalid')
    })
  })

  describe('lightStep', () => {
    it('weight=30 时为 30% 白 + 70% 主色', () => {
      // #409EFF + 30% white = mix(#409EFF, #ffffff, 70%)
      const result = lightStep('#409EFF', 30)
      // 主色 RGB (64,158,255) × 0.7 + 白 (255,255,255) × 0.3 = (184.3, 187.1, 255) ≈ #b8bbff
      const expected = rgbToHex({ r: 64 * 0.7 + 255 * 0.3, g: 158 * 0.7 + 255 * 0.3, b: 255 })
      expect(result.toLowerCase()).toBe(expected.toLowerCase())
    })
  })

  describe('darkStep', () => {
    it('weight=20 时为 20% 黑 + 80% 主色', () => {
      const result = darkStep('#409EFF', 20)
      const expected = rgbToHex({ r: 64 * 0.8, g: 158 * 0.8, b: 255 * 0.8 })
      expect(result.toLowerCase()).toBe(expected.toLowerCase())
    })
  })

  describe('isValidHex', () => {
    it('合法 6 位 HEX', () => {
      expect(isValidHex('#409EFF')).toBe(true)
      expect(isValidHex('409eff')).toBe(true)
    })
    it('非法格式', () => {
      expect(isValidHex('#abc')).toBe(false)
      expect(isValidHex('xyz')).toBe(false)
      expect(isValidHex('')).toBe(false)
    })
  })
})

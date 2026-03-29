import { describe, it, expect } from 'vitest'
import { formatDate, formatDateTime } from '../dateFormat'

describe('dateFormat', () => {
  describe('formatDate', () => {
    it('应正确格式化 Date 对象', () => {
      const date = new Date(2026, 2, 15) // 2026-03-15
      expect(formatDate(date)).toBe('2026-03-15')
    })

    it('应正确格式化毫秒级时间戳', () => {
      // 2026-03-15 08:30:00 UTC
      const ts = new Date(2026, 2, 15, 8, 30, 0).getTime()
      expect(formatDate(ts)).toBe('2026-03-15')
    })

    it('应正确格式化秒级时间戳', () => {
      // 秒级时间戳 < 20000000000
      const ts = Math.floor(new Date(2026, 2, 15).getTime() / 1000)
      expect(formatDate(ts)).toBe('2026-03-15')
    })

    it('应处理 null 返回 -', () => {
      expect(formatDate(null)).toBe('-')
    })

    it('应处理 undefined 返回 -', () => {
      expect(formatDate(undefined)).toBe('-')
    })
  })

  describe('formatDateTime', () => {
    it('应正确格式化 Date 对象', () => {
      const date = new Date(2026, 2, 15, 14, 5, 9)
      expect(formatDateTime(date)).toBe('2026-03-15 14:05:09')
    })

    it('应正确格式化毫秒级时间戳', () => {
      const ts = new Date(2026, 2, 15, 14, 5, 9).getTime()
      expect(formatDateTime(ts)).toBe('2026-03-15 14:05:09')
    })

    it('应处理 null 返回 -', () => {
      expect(formatDateTime(null)).toBe('-')
    })
  })
})

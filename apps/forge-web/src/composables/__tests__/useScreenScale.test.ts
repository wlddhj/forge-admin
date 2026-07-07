import { describe, it, expect, beforeEach } from 'vitest'
import { useScreenScale } from '../useScreenScale'
import { SCREEN_BASE_WIDTH, SCREEN_BASE_HEIGHT } from '@/constants/screen'

describe('useScreenScale', () => {
  const setSize = (w: number, h: number) => {
    Object.defineProperty(window, 'innerWidth', { value: w, configurable: true })
    Object.defineProperty(window, 'innerHeight', { value: h, configurable: true })
  }
  beforeEach(() => { setSize(1920, 1080) })

  it('1920x1080 → scale=1', () => {
    const { scale } = useScreenScale()
    expect(scale.value).toBe(1)
  })

  it('1280x720 → scale=2/3', () => {
    setSize(1280, 720)
    const { scale } = useScreenScale()
    expect(scale.value).toBeCloseTo(1280 / SCREEN_BASE_WIDTH, 5)
  })

  it('containerStyle 固定 1920x1080 + transform', () => {
    const { containerStyle } = useScreenScale()
    expect(containerStyle.value.width).toBe(`${SCREEN_BASE_WIDTH}px`)
    expect(containerStyle.value.height).toBe(`${SCREEN_BASE_HEIGHT}px`)
    expect(containerStyle.value.transform).toContain('scale(')
  })

  it('resize 触发后 scale 更新（150ms 防抖）', async () => {
    const { scale } = useScreenScale()
    setSize(960, 540)
    window.dispatchEvent(new Event('resize'))
    await new Promise(r => setTimeout(r, 200))
    expect(scale.value).toBeCloseTo(0.5, 2)
  })
})
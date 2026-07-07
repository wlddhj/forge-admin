import { describe, it, expect } from 'vitest'
import { useScreenHistory } from '../useScreenHistory'

describe('useScreenHistory', () => {
  it('初始 state 等于 initial', () => {
    const h = useScreenHistory({ v: 0 })
    expect(h.state.value).toEqual({ v: 0 })
    expect(h.canUndo.value).toBe(false)
    expect(h.canRedo.value).toBe(false)
  })

  it('commit 后 canUndo=true', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    expect(h.state.value).toEqual({ v: 1 })
    expect(h.canUndo.value).toBe(true)
  })

  it('undo/redo 双向移动', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.undo()
    expect(h.state.value).toEqual({ v: 1 })
    expect(h.canRedo.value).toBe(true)
    h.undo()
    expect(h.state.value).toEqual({ v: 0 })
    h.redo()
    expect(h.state.value).toEqual({ v: 1 })
  })

  it('新 commit 清空 redo 栈', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.undo()
    h.commit({ v: 3 })
    expect(h.canRedo.value).toBe(false)
  })

  it('max=3 截断历史', () => {
    const h = useScreenHistory({ v: 0 }, { max: 3 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.commit({ v: 3 })
    h.commit({ v: 4 })
    h.undo()
    expect(h.state.value).toEqual({ v: 3 })
    h.undo()
    expect(h.state.value).toEqual({ v: 2 })
    h.undo()
    expect(h.state.value).toEqual({ v: 1 })
  })

  it('clear 同时清空 undo/redo 栈', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.undo()
    h.clear()
    expect(h.canUndo.value).toBe(false)
    expect(h.canRedo.value).toBe(false)
  })
})
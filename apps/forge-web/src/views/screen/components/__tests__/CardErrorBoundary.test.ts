import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import CardErrorBoundary from '../CardErrorBoundary.vue'

// 在 onMounted 里抛错，让 Vue 走组件错误处理链路（onErrorCaptured 才能捕获）。
// setup() 同步抛错会直接冒到 mount 调用方，不经过 Vue 的错误处理。
const ThrowOnMount = defineComponent({
  setup: () => {
    return () => h('div')
  },
  mounted() { throw new Error('boom') }
})

describe('CardErrorBoundary', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('正常渲染时透传默认插槽', () => {
    const Child = defineComponent({ setup: () => () => h('div', { class: 'ok' }, 'hello') })
    const wrapper = mount(CardErrorBoundary, { slots: { default: () => h(Child) } })
    expect(wrapper.find('.ok').exists()).toBe(true)
    expect(wrapper.text()).toBe('hello')
  })

  it('子组件抛错时显示降级占位 + 5s 后自动重试', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(CardErrorBoundary, {
      props: { retryAfterMs: 5000, onRetry },
      slots: { default: () => h(ThrowOnMount) }
    })
    await nextTick()
    expect(wrapper.find('.card-error-fallback').exists()).toBe(true)
    expect(wrapper.text()).toContain('数据加载失败')
    vi.advanceTimersByTime(5000)
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('手动重试按钮触发 onRetry', async () => {
    const onRetry = vi.fn()
    const wrapper = mount(CardErrorBoundary, {
      props: { onRetry },
      slots: { default: () => h(ThrowOnMount) }
    })
    await nextTick()
    await wrapper.find('.card-error-retry').trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})

import { ref, onMounted, onUnmounted, nextTick, type Ref } from 'vue'

/**
 * 抽屉/弹窗内表格高度自适应 Hook
 * 基于容器自身高度动态计算表格可用高度，适用于在 el-drawer、el-dialog 等容器内使用表格的场景
 */
export interface UseDrawerTableHeightOptions {
  /** 容器元素的 ref */
  containerRef: Ref<HTMLElement | undefined>
  /** 需要排除高度的子元素选择器列表（如搜索栏、工具栏） */
  excludeSelectors?: string[]
  /** 额外偏移量 */
  extraOffset?: number
}

export function useDrawerTableHeight(options: UseDrawerTableHeightOptions) {
  const { containerRef, excludeSelectors = [], extraOffset = 0 } = options
  const tableHeight = ref(400)

  const calcHeight = () => {
    const container = containerRef.value
    if (!container) return
    let excluded = 0
    for (const selector of excludeSelectors) {
      const el = container.querySelector(selector)
      if (el) {
        const style = window.getComputedStyle(el)
        excluded += el.offsetHeight
          + (parseFloat(style.marginTop) || 0)
          + (parseFloat(style.marginBottom) || 0)
      }
    }
    tableHeight.value = Math.max(container.clientHeight - excluded - extraOffset, 200)
  }

  let observer: ResizeObserver | null = null

  onMounted(() => {
    nextTick(() => {
      calcHeight()
      if (containerRef.value) {
        observer = new ResizeObserver(calcHeight)
        observer.observe(containerRef.value)
      }
    })
  })

  onUnmounted(() => {
    observer?.disconnect()
  })

  return { tableHeight, updateHeight: calcHeight }
}

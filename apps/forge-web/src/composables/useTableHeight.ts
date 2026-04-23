import { ref, onMounted, onUnmounted, nextTick } from 'vue'

/**
 * 表格高度自适应 Hook
 * 根据窗口大小动态计算表格高度，铺满屏幕剩余区域
 */
export interface UseTableHeightOptions {
  /** 搜索栏选择器 */
  searchCardSelector?: string
  /** 表格卡片选择器 */
  tableCardSelector?: string
  /** 额外偏移量 */
  extraOffset?: number
  /** 是否预留分页高度 */
  hasPagination?: boolean
}

export function useTableHeight(options: UseTableHeightOptions = {}) {
  const {
    searchCardSelector = '.search-card',
    extraOffset = 0,
    hasPagination = true
  } = options

  const tableHeight = ref<number>(400)

  /**
   * 获取元素高度（包括 margin）
   */
  const getElementHeight = (selector: string): number => {
    const el = document.querySelector(selector)
    if (!el) return 0
    const style = window.getComputedStyle(el)
    const marginTop = parseFloat(style.marginTop) || 0
    const marginBottom = parseFloat(style.marginBottom) || 0
    return el.offsetHeight + marginTop + marginBottom
  }

  /**
   * 计算并更新表格高度
   */
  const calcTableHeight = () => {
    const viewportHeight = window.innerHeight

    // Header 高度
    const headerHeight = getElementHeight('.layout-header') || 60

    // TabsView 高度
    const tabsViewHeight = getElementHeight('.tabs-view') || 0

    // 内容区 padding (上下各 20px)
    const contentPadding = 40

    // 搜索栏高度
    const searchCardHeight = getElementHeight(searchCardSelector) || 0

    // vxe-toolbar 高度
    const toolbarHeight = getElementHeight('.vxe-toolbar') || 0

    // 分页高度 (预留)
    const paginationHeight = hasPagination ? getElementHeight('.el-pagination') || 52 : 0

    // 卡片内边距 (el-card__body padding)
    const cardPadding = 5

    // 计算表格高度
    const height = viewportHeight
        - headerHeight
        - tabsViewHeight
        - contentPadding
        - searchCardHeight
        - toolbarHeight
        - paginationHeight
        - cardPadding
        - extraOffset

    // 确保最小高度
    tableHeight.value = Math.max(height, 200)
  }

  let resizeTimer: ReturnType<typeof setTimeout> | null = null

  const handleResize = () => {
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(calcTableHeight, 100)
  }

  onMounted(() => {
    // 等待 DOM 渲染完成
    nextTick(() => {
      calcTableHeight()
    })
    window.addEventListener('resize', handleResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    if (resizeTimer) clearTimeout(resizeTimer)
  })

  return {
    tableHeight,
    updateHeight: calcTableHeight
  }
}
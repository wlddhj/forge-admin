import { ref, onMounted, onUnmounted, nextTick } from 'vue'

/**
 * 表格高度自适应 Hook（增量校正法）
 *
 * 旧实现逐项枚举上方元素高度做视口减法（.search-card/.vxe-toolbar 等），页面里的
 * el-alert、卡片 header、表格下方的 policy-tip 等都不在清单内，高度算大导致页面
 * 溢出滚动，各页面只能靠 extraOffset 写死补偿（12~140 不等），alert 显隐/文案换行
 * 又会让补偿失准。
 *
 * 现改为实测校正：以「页面容器（.app-container）底边贴合视口底」为目标，实测容器
 * 当前底边与目标的差值 delta，直接从表格高度中扣除。表格高度是容器高度的线性变量，
 * 一步收敛不振荡；容器内表格上下的全部内容（搜索卡、alert、tabs、工具栏、卡片
 * header、分页、页脚说明等）无论显隐、换行、增删，全部自动计入，页面无需任何参数。
 *
 * 兼容说明：hasPagination/extraOffset 参数保留；searchCardSelector/tableCardSelector
 * 仅为旧签名兼容保留，已不参与计算。
 */
export interface UseTableHeightOptions {
  /** @deprecated 旧签名兼容保留，已不参与计算 */
  searchCardSelector?: string
  /** @deprecated 旧签名兼容保留，已不参与计算 */
  tableCardSelector?: string
  /** 额外偏移量（特殊场景微调，正常情况无需传） */
  extraOffset?: number
  /** @deprecated 已不参与计算（表格下方内容经 delta 实测自动计入，无需开关） */
  hasPagination?: boolean
  /** 表格元素选择器；默认依次取 .table-card 内可见的 vxe-table、页面上可见的 vxe-table */
  tableSelector?: string
}

export function useTableHeight(options: UseTableHeightOptions = {}) {
  const { extraOffset = 0, tableSelector } = options

  const tableHeight = ref<number>(400)

  /**
   * 页面主表格：同一页面可能存在多个 vxe-table（el-tabs 切换的 pane、弹窗内表格），
   * 取第一个可见的（offsetParent 为 null 即 display:none）；主表格通常在 .table-card
   * 内，优先匹配以避开弹窗 teleport 节点
   */
  const findTableEl = (): HTMLElement | null => {
    const selectors = tableSelector ? [tableSelector] : ['.table-card .vxe-table', '.vxe-table']
    for (const selector of selectors) {
      for (const el of document.querySelectorAll<HTMLElement>(selector)) {
        if (el.offsetParent !== null) return el
      }
    }
    return null
  }

  const calcTableHeight = () => {
    const tableEl = findTableEl()
    // 表格未渲染（如未选账套的 v-else 分支）时保持上次值，等挂载后由 ResizeObserver 重算
    if (!tableEl) return

    // 目标：页面容器底边贴合视口底（无容器结构时退化为表格底贴合视口底）
    const container = tableEl.closest('.app-container')
    const currentBottom = (container ?? tableEl).getBoundingClientRect().bottom
    const delta = Math.round(currentBottom - window.innerHeight + extraOffset+10)
    if (delta === 0) return
    // 容器内其余元素高度不随表格变化，delta 完全由表格高度决定：一步扣除即收敛
    const next = Math.max(tableHeight.value - delta, 200)
    // 同值守卫：ResizeObserver 会因表格/容器尺寸变化再次触发，值不变时直接返回
    if (next !== tableHeight.value) tableHeight.value = next
  }

  let resizeTimer: ReturnType<typeof setTimeout> | null = null
  const handleResize = () => {
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(calcTableHeight, 100)
  }

  // ResizeObserver：观察页面容器——alert 显隐、表格初次挂载、tabs 切换引起的容器
  // 尺寸变化都会触发重算；视口尺寸变化由 window resize 监听兜底
  let observer: ResizeObserver | null = null
  let observedEl: Element | null = null
  const attachObserver = () => {
    const tableEl = findTableEl()
    const target = tableEl?.closest('.app-container')
        ?? document.querySelector('.table-card')
        ?? document.body
    if (observedEl === target) return
    observer?.disconnect()
    observedEl = target
    observer = new ResizeObserver(calcTableHeight)
    observer.observe(target)
  }

  onMounted(() => {
    // 等待 DOM 渲染完成（表格可能由 v-if/数据加载延迟挂载，挂载后由 observer 接管重算）
    nextTick(() => {
      attachObserver()
      calcTableHeight()
    })
    window.addEventListener('resize', handleResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    if (resizeTimer) clearTimeout(resizeTimer)
    observer?.disconnect()
  })

  return {
    tableHeight,
    updateHeight: calcTableHeight
  }
}

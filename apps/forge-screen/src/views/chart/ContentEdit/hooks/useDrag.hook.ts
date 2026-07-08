import { toRaw } from 'vue'
import { DragKeyEnum, MouseEventButton } from '@/enums/editPageEnum'
import { createComponent } from '@/packages'
import { ConfigType } from '@/packages/index.d'
import { CreateComponentType, CreateComponentGroupType, PickCreateComponentType } from '@/packages/index.d'
import { useContextMenu } from '@/views/chart/hooks/useContextMenu.hook'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { useSettingStore } from '@/store/modules/settingStore/settingStore'
import { EditCanvasTypeEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { selectBoxIndex } from '@/settings/designSetting'
import { loadingStart, loadingFinish, loadingError, setComponentPosition, JSONParse } from '@/utils'
import { throttle, cloneDeep } from 'lodash'

const chartEditStore = useChartEditStore()
const { onClickOutSide } = useContextMenu()

// * 拖拽到编辑区域里
export const dragHandle = async (e: DragEvent) => {
  e.preventDefault()

  try {
    loadingStart()

    // 获取拖拽数据
    const drayDataString = e!.dataTransfer!.getData(DragKeyEnum.DRAG_KEY)
    if (!drayDataString) {
      loadingFinish()
      return
    }

    // 修改状态
    chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_CREATE, false)
    const dropData: Exclude<ConfigType, ['image']> = JSONParse(drayDataString)
    if (dropData.disabled) return

    // 创建新图表组件
    let newComponent: CreateComponentType = await createComponent(dropData)
    if (dropData.redirectComponent) {
      dropData.dataset && (newComponent.option.dataset = dropData.dataset)
      newComponent.chartConfig.title = dropData.title
      newComponent.chartConfig.chartFrame = dropData.chartFrame
    }

    setComponentPosition(newComponent, e.offsetX - newComponent.attr.w / 2, e.offsetY - newComponent.attr.h / 2)
    chartEditStore.addComponentList(newComponent, false, true)
    chartEditStore.setTargetSelectChart(newComponent.id)
    loadingFinish()
  } catch (error) {
    loadingError()
    window['$message'].warning(`图表正在研发中, 敬请期待...`)
  }
}

// * 进入拖拽区域
export const dragoverHandle = (e: DragEvent) => {
  e.preventDefault()
  e.stopPropagation()

  if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
}

// * 不拦截默认行为点击
export const mousedownHandleUnStop = (e: MouseEvent, item?: CreateComponentType | CreateComponentGroupType) => {
  if (item) {
    chartEditStore.setTargetSelectChart(item.id)
    return
  }
  chartEditStore.setTargetSelectChart(undefined)
}

// * 框选
export const mousedownBoxSelect = (e: MouseEvent, item?: CreateComponentType | CreateComponentGroupType) => {
  if (e.which == 2) return
  if (window.$KeyboardActive?.space) return

  mousedownHandleUnStop(e)

  // 框选容器（.go-edit-range），框选框会 append 到这里
  const container = e.currentTarget as HTMLElement
  // 主题色（每次框选时读取，跟随主题切换）
  const themeColor = useDesignStore().getAppTheme

  // 创建框选框 DOM（鼠标抬起时销毁，不走 store 响应式，避免卡顿）
  // 将主题色（hex）转为带透明度的背景色
  const hexToRgba = (hex: string, alpha: number) => {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    return `rgba(${r},${g},${b},${alpha})`
  }
  const selectEl = document.createElement('div')
  selectEl.style.cssText = `position:absolute;left:0;top:0;z-index:${selectBoxIndex};pointer-events:none;border:1px dashed ${themeColor};background:${hexToRgba(themeColor, 0.06)};`
  container.appendChild(selectEl)

  // 记录点击初始位置
  const startOffsetX = e.offsetX
  const startOffsetY = e.offsetY
  const startScreenX = e.screenX
  const startScreenY = e.screenY

  // 记录缩放
  const scale = chartEditStore.getEditCanvas.scale

  // 移动框选
  const mousemove = throttle((moveEvent: MouseEvent) => {
    // 取消当前选中
    chartEditStore.setTargetSelectChart()
    chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_SELECT, true)

    // 这里先把相对值算好，不然组件无法获取 startScreenX 和 startScreenY 的值
    const currX = startOffsetX + moveEvent.screenX - startScreenX
    const currY = startOffsetY + moveEvent.screenY - startScreenY

    // 计算框选的左上角和右下角
    const selectAttr = {
      // 左上角
      x1: 0,
      y1: 0,
      // 右下角
      x2: 0,
      y2: 0
    }
    if (currX > startOffsetX && currY > startOffsetY) {
      // 右下方向
      selectAttr.x1 = startOffsetX
      selectAttr.y1 = startOffsetY
      selectAttr.x2 = Math.round(startOffsetX + (moveEvent.screenX - startScreenX) / scale)
      selectAttr.y2 = Math.round(startOffsetY + (moveEvent.screenY - startScreenY) / scale)
    } else if (currX > startOffsetX && currY < startOffsetY) {
      // 右上方向
      selectAttr.x1 = startOffsetX
      selectAttr.y1 = Math.round(startOffsetY - (startScreenY - moveEvent.screenY) / scale)
      selectAttr.x2 = Math.round(startOffsetX + (moveEvent.screenX - startScreenX) / scale)
      selectAttr.y2 = startOffsetY
    } else if (currX < startOffsetX && currY > startOffsetY) {
      selectAttr.x1 = Math.round(startOffsetX - (startScreenX - moveEvent.screenX) / scale)
      selectAttr.y1 = startOffsetY
      selectAttr.x2 = startOffsetX
      selectAttr.y2 = Math.round(startOffsetY + (moveEvent.screenY - startScreenY) / scale)
      // 左下方向
    } else {
      // 左上方向
      selectAttr.x1 = Math.round(startOffsetX - (startScreenX - moveEvent.screenX) / scale)
      selectAttr.y1 = Math.round(startOffsetY - (startScreenY - moveEvent.screenY) / scale)
      selectAttr.x2 = startOffsetX
      selectAttr.y2 = startOffsetY
    }

    // 直接操作 DOM 绘制框选框，绕开 store 响应式
    const left = Math.min(selectAttr.x1, selectAttr.x2)
    const top = Math.min(selectAttr.y1, selectAttr.y2)
    const width = Math.abs(selectAttr.x2 - selectAttr.x1)
    const height = Math.abs(selectAttr.y2 - selectAttr.y1)
    selectEl.style.left = `${left}px`
    selectEl.style.top = `${top}px`
    selectEl.style.width = `${width}px`
    selectEl.style.height = `${height}px`

    // 遍历组件
    chartEditStore.getComponentList.forEach(item => {
      if (!chartEditStore.getTargetChart.selectId.includes(item.id)) {
        // 处理左上角
        const { x, y, w, h } = item.attr
        const targetAttr = {
          // 左上角
          x1: x,
          y1: y,
          // 右下角
          x2: x + w,
          y2: y + h
        }
        // 部分相交即选中
        if (
          targetAttr.x1 < selectAttr.x2 &&
          targetAttr.x2 > selectAttr.x1 &&
          targetAttr.y1 < selectAttr.y2 &&
          targetAttr.y2 > selectAttr.y1 &&
          !item.status.lock &&
          !item.status.hide
        ) {
          chartEditStore.setTargetSelectChart(item.id, true)
        }
      }
    })
  }, 30)

  // 鼠标抬起
  const mouseup = () => {
    // 鼠标抬起时，结束mousemove的节流函数，避免选框不消失问题
    mousemove.cancel()
    chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_SELECT, false)
    chartEditStore.setMousePosition(0, 0, 0, 0)
    // 销毁框选框 DOM
    selectEl.remove()
    document.removeEventListener('mousemove', mousemove)
    document.removeEventListener('mouseup', mouseup)
  }
  document.addEventListener('mousemove', mousemove)
  document.addEventListener('mouseup', mouseup)
}

// ---- 对齐线 DOM 操作（纯命令式，不走 Vue 响应式）----

let _alignEls: HTMLElement[] = []

const _clearAlignLines = () => {
  _alignEls.forEach(el => el.remove())
  _alignEls = []
}

const _drawAlignLine = (
  container: HTMLElement,
  isRow: boolean,
  fixedCoord: number,
  spanStart: number,
  spanEnd: number,
  label: string,
  color: string
) => {
  if (spanStart >= spanEnd) return
  const el = document.createElement('div')
  if (isRow) {
    // 横线：固定 top，left~width 表示水平范围
    el.style.cssText = `position:absolute;pointer-events:none;z-index:9999;background:${color};left:${spanStart}px;top:${fixedCoord}px;width:${spanEnd - spanStart}px;height:1px;`
  } else {
    // 竖线：固定 left，top~height 表示垂直范围
    el.style.cssText = `position:absolute;pointer-events:none;z-index:9999;background:${color};left:${fixedCoord}px;top:${spanStart}px;width:1px;height:${spanEnd - spanStart}px;`
  }
  const tag = document.createElement('span')
  tag.textContent = label
  tag.style.cssText = `position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);background:${color};color:#fff;font-size:11px;line-height:1;padding:2px 5px;border-radius:2px;white-space:nowrap;`
  el.appendChild(tag)
  container.appendChild(el)
  _alignEls.push(el)
}

type AlignRef = { id: string; attr: { x: number; y: number; w: number; h: number } }

/**
 * 吸附对齐：检测 sel 与所有 refs 的边界对齐，返回吸附后坐标并绘制对齐线。
 * 对齐线只绘制两个对象之间的覆盖范围，中间显示吸附前偏差 px。
 */
const _runAlignSnap = (
  container: HTMLElement,
  selectId: string,
  sel: { x: number; y: number; w: number; h: number },
  refs: AlignRef[],
  color: string,
  minDist: number
): { x: number; y: number } => {
  _clearAlignLines()

  const { x: sx, y: sy, w: sw, h: sh } = sel

  type Hit = { coord: number; newOrig: number; delta: number; ref: AlignRef }
  let bestX: Hit | null = null
  let bestY: Hit | null = null

  for (const ref of refs) {
    if (ref.id === selectId) continue
    const { x: cx, y: cy, w: cw, h: ch } = ref.attr
    const cR = cx + cw, cB = cy + ch
    const cCx = cx + cw / 2, cCy = cy + ch / 2

    // X 对齐检测 → 竖线
    // [sel边, ref边, 吸附后的新 sx]
    const xChecks: [number, number, number][] = [
      [sx,        cx,  cx],          // 左-左
      [sx,        cCx, cCx],         // 左-中
      [sx,        cR,  cR],          // 左-右
      [sx+sw/2,   cx,  cx-sw/2],     // 中-左
      [sx+sw/2,   cCx, cCx-sw/2],    // 中-中
      [sx+sw/2,   cR,  cR-sw/2],     // 中-右
      [sx+sw,     cx,  cx-sw],       // 右-左
      [sx+sw,     cCx, cCx-sw],      // 右-中
      [sx+sw,     cR,  cR-sw],       // 右-右
    ]
    for (const [a, b, newSx] of xChecks) {
      const delta = Math.abs(a - b)
      if (delta <= minDist && (!bestX || delta < bestX.delta)) {
        bestX = { coord: b, newOrig: newSx, delta, ref }
      }
    }

    // Y 对齐检测 → 横线
    const yChecks: [number, number, number][] = [
      [sy,        cy,  cy],
      [sy,        cCy, cCy],
      [sy,        cB,  cB],
      [sy+sh/2,   cy,  cy-sh/2],
      [sy+sh/2,   cCy, cCy-sh/2],
      [sy+sh/2,   cB,  cB-sh/2],
      [sy+sh,     cy,  cy-sh],
      [sy+sh,     cCy, cCy-sh],
      [sy+sh,     cB,  cB-sh],
    ]
    for (const [a, b, newSy] of yChecks) {
      const delta = Math.abs(a - b)
      if (delta <= minDist && (!bestY || delta < bestY.delta)) {
        bestY = { coord: b, newOrig: newSy, delta, ref }
      }
    }
  }

  const finalX = bestX ? Math.round(bestX.newOrig) : sx
  const finalY = bestY ? Math.round(bestY.newOrig) : sy

  if (bestX) {
    const { y: ry, h: rh } = bestX.ref.attr
    // 两组件在 Y 方向上最近的两条边（间隙区域）
    const highBottom = Math.min(finalY + sh, ry + rh)
    const lowTop = Math.max(finalY, ry)
    const spanStart = Math.min(highBottom, lowTop)
    const spanEnd = Math.max(highBottom, lowTop)
    const gapDist = Math.max(0, lowTop - highBottom)
    _drawAlignLine(container, false, bestX.coord, spanStart, spanEnd, `${Math.round(gapDist)}px`, color)
  }
  if (bestY) {
    const { x: rx, w: rw } = bestY.ref.attr
    // 两组件在 X 方向上最近的两条边（间隙区域）
    const leftRight = Math.min(finalX + sw, rx + rw)
    const rightLeft = Math.max(finalX, rx)
    const spanStart = Math.min(leftRight, rightLeft)
    const spanEnd = Math.max(leftRight, rightLeft)
    const gapDist = Math.max(0, rightLeft - leftRight)
    _drawAlignLine(container, true, bestY.coord, spanStart, spanEnd, `${Math.round(gapDist)}px`, color)
  }

  return { x: finalX, y: finalY }
}

// * 鼠标事件
export const useMouseHandle = () => {
  // *  Click 事件, 松开鼠标触发
  const mouseClickHandle = (e: MouseEvent, item: CreateComponentType | CreateComponentGroupType) => {
    e.preventDefault()
    e.stopPropagation()
    if (item.status.lock) return
    // 若此时按下了 CTRL, 表示多选
    if (window.$KeyboardActive?.ctrl) {
      // 若已选中，则去除
      if (chartEditStore.targetChart.selectId.includes(item.id)) {
        const exList = chartEditStore.targetChart.selectId.filter(e => e !== item.id)
        chartEditStore.setTargetSelectChart(exList)
      } else {
        chartEditStore.setTargetSelectChart(item.id, true)
      }
    }
  }

  // * 按下事件（包含移动事件）
  const mousedownHandle = (e: MouseEvent, item: CreateComponentType | CreateComponentGroupType) => {
    e.preventDefault()
    e.stopPropagation()
    if (item.status.lock) return
    onClickOutSide()
    // 按下左键 + CTRL
    if (e.buttons === MouseEventButton.LEFT && window.$KeyboardActive?.ctrl) return

    // 按下右键 + 选中多个 + 目标元素是多选子元素
    const selectId = chartEditStore.getTargetChart.selectId
    if (e.buttons === MouseEventButton.RIGHT && selectId.length > 1 && selectId.includes(item.id)) return

    // 选中当前目标组件
    chartEditStore.setTargetSelectChart(item.id)

    // 按下右键
    if (e.buttons === MouseEventButton.RIGHT) return

    const scale = chartEditStore.getEditCanvas.scale
    // 读取对齐配置（在 mousedown 时快照，避免拖拽过程中反复读 store）
    const themeColor = useDesignStore().getAppTheme
    const minDist = useSettingStore().getChartAlignRange

    // 记录图表初始位置和大小
    const targetMap = new Map()
    chartEditStore.getTargetChart.selectId.forEach(id => {
      const index = chartEditStore.fetchTargetIndex(id)
      if (index !== -1) {
        const { x, y, w, h } = toRaw(chartEditStore.getComponentList[index]).attr
        targetMap.set(id, { x, y, w, h })
      }
    })

    // 记录点击初始位置
    const startX = e.screenX
    const startY = e.screenY

    // 记录历史位置
    let prevComponentInstance: Array<CreateComponentType | CreateComponentGroupType> = []
    chartEditStore.getTargetChart.selectId.forEach(id => {
      if (!targetMap.has(id)) return
      const index = chartEditStore.fetchTargetIndex(id)
      prevComponentInstance.push(cloneDeep(chartEditStore.getComponentList[index]))
    })

    // 记录初始位置
    chartEditStore.setMousePosition(undefined, undefined, startX, startY)

    // 对齐线直接绘制到画布容器
    const alignContainer = document.querySelector('.go-edit-range') as HTMLElement | null

    // 移动-计算偏移量
    const mousemove = throttle((moveEvent: MouseEvent) => {
      chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_DRAG, true)
      chartEditStore.setMousePosition(moveEvent.screenX, moveEvent.screenY)

      const offsetX = (moveEvent.screenX - startX) / scale
      const offsetY = (moveEvent.screenY - startY) / scale

      const currentSelectIds = chartEditStore.getTargetChart.selectId

      if (currentSelectIds.length === 1) {
        // 单选：做对齐吸附
        const id = currentSelectIds[0]
        if (!targetMap.has(id)) return
        const { x, y, w, h } = targetMap.get(id)
        let currX = Math.round(x + offsetX)
        let currY = Math.round(y + offsetY)

        if (alignContainer) {
          // 构造参照列表（所有其他组件 + 画布）
          // 直接引用 attr，避免每帧构造新对象
          const refs: AlignRef[] = (chartEditStore.getComponentList as Array<CreateComponentType | CreateComponentGroupType>).map(c => ({
            id: c.id,
            attr: c.attr as { x: number; y: number; w: number; h: number }
          }))
          refs.push({
            id: '__canvas__',
            attr: { x: 0, y: 0, w: chartEditStore.getEditCanvasConfig.width, h: chartEditStore.getEditCanvasConfig.height }
          })
          const snapped = _runAlignSnap(alignContainer, id, { x: currX, y: currY, w, h }, refs, themeColor, minDist)
          currX = snapped.x
          currY = snapped.y
        }

        const index = chartEditStore.fetchTargetIndex(id)
        const componentInstance = chartEditStore.getComponentList[index]
        if (componentInstance) {
          componentInstance.attr = Object.assign(componentInstance.attr, { x: currX, y: currY })
        }
      } else {
        // 多选：无对齐，直接移动
        _clearAlignLines()
        currentSelectIds.forEach(id => {
          if (!targetMap.has(id)) return
          const index = chartEditStore.fetchTargetIndex(id)
          const { x, y } = targetMap.get(id)
          const componentInstance = chartEditStore.getComponentList[index]
          if (componentInstance) {
            componentInstance.attr = Object.assign(componentInstance.attr, {
              x: Math.round(x + offsetX),
              y: Math.round(y + offsetY)
            })
          }
        })
      }
    }, 20)

    const mouseup = () => {
      try {
        _clearAlignLines()
        chartEditStore.setMousePosition(0, 0, 0, 0)
        chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_DRAG, false)
        // 加入历史栈
        if (prevComponentInstance.length) {
          chartEditStore.getTargetChart.selectId.forEach(id => {
            if (!targetMap.has(id)) return
            const index = chartEditStore.fetchTargetIndex(id)
            const curComponentInstance = chartEditStore.getComponentList[index]
            // 找到记录的所选组件
            prevComponentInstance.forEach(preItem => {
              if (preItem.id === id) {
                preItem.attr = Object.assign(preItem.attr, {
                  offsetX: curComponentInstance.attr.x - preItem.attr.x,
                  offsetY: curComponentInstance.attr.y - preItem.attr.y
                })
              }
            })
          })

          const moveComponentInstance = prevComponentInstance.filter(
            item => item.attr.offsetX !== 0 && item.attr.offsetY !== 0
          )
          moveComponentInstance.length && chartEditStore.moveComponentList(moveComponentInstance)
        }
        document.removeEventListener('mousemove', mousemove)
        document.removeEventListener('mouseup', mouseup)
      } catch (err) {
        console.log(err)
      }
    }

    document.addEventListener('mousemove', mousemove)
    document.addEventListener('mouseup', mouseup)
  }

  // * 进入事件
  const mouseenterHandle = (e: MouseEvent, item: CreateComponentType | CreateComponentGroupType) => {
    e.preventDefault()
    e.stopPropagation()
    if (!chartEditStore.getEditCanvas.isSelect) {
      chartEditStore.setTargetHoverChart(item.id)
    }
  }

  // * 移出事件
  const mouseleaveHandle = (e: MouseEvent, item: CreateComponentType | CreateComponentGroupType) => {
    e.preventDefault()
    e.stopPropagation()
    chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_DRAG, false)
    chartEditStore.setTargetHoverChart(undefined)
  }

  return { mouseClickHandle, mousedownHandle, mouseenterHandle, mouseleaveHandle }
}

// * 移动锚点
export const useMousePointHandle = (e: MouseEvent, point: string, attr: PickCreateComponentType<'attr'>) => {
  e.stopPropagation()
  e.preventDefault()

  // 设置拖拽状态
  chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_DRAG, true)
  const scale = chartEditStore.getEditCanvas.scale

  const itemAttrX = attr.x
  const itemAttrY = attr.y
  const itemAttrW = attr.w
  const itemAttrH = attr.h

  // 记录点击初始位置
  const startX = e.screenX
  const startY = e.screenY

  // 记录初始位置
  chartEditStore.setMousePosition(startX, startY)

  const mousemove = throttle((moveEvent: MouseEvent) => {
    chartEditStore.setMousePosition(moveEvent.screenX, moveEvent.screenY)

    let currX = Math.round((moveEvent.screenX - startX) / scale)
    let currY = Math.round((moveEvent.screenY - startY) / scale)

    const isTop = /t/.test(point)
    const isBottom = /b/.test(point)
    const isLeft = /l/.test(point)
    const isRight = /r/.test(point)

    const newHeight = itemAttrH + (isTop ? -currY : isBottom ? currY : 0)
    const newWidth = itemAttrW + (isLeft ? -currX : isRight ? currX : 0)

    attr.h = newHeight > 0 ? newHeight : 0
    attr.w = newWidth > 0 ? newWidth : 0
    attr.x = itemAttrX + (isLeft ? currX : 0)
    attr.y = itemAttrY + (isTop ? currY : 0)
  }, 50)

  const mouseup = () => {
    chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_DRAG, false)
    chartEditStore.setMousePosition(0, 0, 0, 0)
    document.removeEventListener('mousemove', mousemove)
    document.removeEventListener('mouseup', mouseup)
  }

  document.addEventListener('mousemove', mousemove)
  document.addEventListener('mouseup', mouseup)
}

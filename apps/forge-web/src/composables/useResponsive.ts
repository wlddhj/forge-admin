import { ref, computed, onMounted, onUnmounted, type ComputedRef, type Ref } from 'vue'

/**
 * 响应式断点配置
 */
export const BREAKPOINTS = {
  xs: 375,      // 超小屏（小手机）
  sm: 640,      // 小屏（大手机）
  md: 768,      // 中等屏（平板竖屏）
  lg: 1024,     // 大屏（平板横屏/小笔记本）
  xl: 1280,     // 超大屏（桌面）
  '2xl': 1536   // 2K 屏幕
}

/**
 * 设备类型
 */
export type DeviceType = 'mobile' | 'tablet' | 'desktop'

/**
 * 响应式状态
 */
export interface ResponsiveState {
  width: number
  height: number
  deviceType: DeviceType
  isMobile: boolean
  isTablet: boolean
  isDesktop: boolean
  isTouch: boolean
  isIOS: boolean
  isAndroid: boolean
}

/**
 * 响应式 Hook
 */
export function useResponsive() {
  const width = ref(window.innerWidth)
  const height = ref(window.innerHeight)

  // 检测是否为触摸设备
  const isTouch = (): boolean => {
    return 'ontouchstart' in window ||
      navigator.maxTouchPoints > 0 ||
      (navigator as any).msMaxTouchPoints > 0
  }

  // 检测 iOS 设备
  const isIOS = (): boolean => {
    const userAgent = navigator.userAgent.toLowerCase()
    return /iphone|ipad|ipod/.test(userAgent)
  }

  // 检测 Android 设备
  const isAndroid = (): boolean => {
    const userAgent = navigator.userAgent.toLowerCase()
    return /android/.test(userAgent)
  }

  // 计算设备类型
  const deviceType = computed<DeviceType>(() => {
    if (width.value < BREAKPOINTS.md) {
      return 'mobile'
    } else if (width.value < BREAKPOINTS.lg) {
      return 'tablet'
    } else {
      return 'desktop'
    }
  })

  // 响应式状态
  const state = computed<ResponsiveState>(() => ({
    width: width.value,
    height: height.value,
    deviceType: deviceType.value,
    isMobile: deviceType.value === 'mobile',
    isTablet: deviceType.value === 'tablet',
    isDesktop: deviceType.value === 'desktop',
    isTouch: isTouch(),
    isIOS: isIOS(),
    isAndroid: isAndroid()
  }))

  // 更新窗口尺寸
  const updateSize = () => {
    width.value = window.innerWidth
    height.value = window.innerHeight
  }

  // 节流处理窗口大小变化
  let resizeTimer: ReturnType<typeof setTimeout> | null = null
  const handleResize = () => {
    if (resizeTimer) {
      clearTimeout(resizeTimer)
    }
    resizeTimer = setTimeout(updateSize, 100)
  }

  onMounted(() => {
    window.addEventListener('resize', handleResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    if (resizeTimer) {
      clearTimeout(resizeTimer)
    }
  })

  return {
    width: state.value.width,
    height: state.value.height,
    deviceType: state.value.deviceType,
    isMobile: computed(() => state.value.isMobile),
    isTablet: computed(() => state.value.isTablet),
    isDesktop: computed(() => state.value.isDesktop),
    isTouch: computed(() => state.value.isTouch),
    isIOS: computed(() => state.value.isIOS),
    isAndroid: computed(() => state.value.isAndroid),
    BREAKPOINTS
  }
}

import { ref, computed, getCurrentScope, onScopeDispose, type Ref, type ComputedRef } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { SCREEN_BASE_WIDTH, SCREEN_BASE_HEIGHT } from '@/constants/screen'

export interface ScreenScale {
  width: Ref<number>
  height: Ref<number>
  scale: ComputedRef<number>
  containerStyle: ComputedRef<Record<string, string>>
}

export function useScreenScale(): ScreenScale {
  const width = ref(typeof window !== 'undefined' ? window.innerWidth : SCREEN_BASE_WIDTH)
  const height = ref(typeof window !== 'undefined' ? window.innerHeight : SCREEN_BASE_HEIGHT)

  const scale = computed(() =>
    Math.min(width.value / SCREEN_BASE_WIDTH, height.value / SCREEN_BASE_HEIGHT)
  )

  const containerStyle = computed(() => {
    const s = scale.value
    return {
      width: `${SCREEN_BASE_WIDTH}px`,
      height: `${SCREEN_BASE_HEIGHT}px`,
      transform: `scale(${s})`,
      'transform-origin': 'center top',
      position: 'absolute' as const,
      left: '50%',
      top: '0',
      'margin-left': `-${(SCREEN_BASE_WIDTH * s) / 2}px`
    }
  })

  const update = () => {
    width.value = window.innerWidth
    height.value = window.innerHeight
  }
  const debouncedUpdate = useDebounceFn(update, 150)

  // 立即注册 resize listener（不再依赖组件 onMounted），
  // 配合 getCurrentScope/onScopeDispose 让组件卸载时自动清理。
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', debouncedUpdate)
    if (getCurrentScope()) {
      onScopeDispose(() => window.removeEventListener('resize', debouncedUpdate))
    }
  }

  return { width, height, scale, containerStyle }
}
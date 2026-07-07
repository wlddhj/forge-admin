import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ScreenDetailResponse } from '@/api/screen'

export const useScreenStore = defineStore('screen', () => {
  const activeScreen = ref<ScreenDetailResponse | null>(null)
  const setActive = (s: ScreenDetailResponse | null) => { activeScreen.value = s }
  const clear = () => { activeScreen.value = null }
  return { activeScreen, setActive, clear }
})

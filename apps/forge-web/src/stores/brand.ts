/**
 * 品牌配置状态管理
 *
 * 启动时从公开接口加载（免登录），接口异常或字段为空时回退到构建期 .env 默认值，
 * 保证登录页在任何情况下可渲染。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { brandApi, type BrandConfig } from '@/api/system/brand'
import { usePageConfigStore } from '@/stores/pageConfig'

const DEFAULT_BRAND: BrandConfig = {
  logo: '',
  name: import.meta.env.VITE_APP_TITLE,
  loginTitle: import.meta.env.VITE_APP_TITLE,
  loginSubtitle: import.meta.env.VITE_APP_SUBTITLE
}

export const useBrandStore = defineStore('brand', () => {
  const brand = ref<BrandConfig>({ ...DEFAULT_BRAND })
  const loaded = ref(false)

  /** Logo 地址（未配置时回退系统默认） */
  const logoUrl = computed(() => brand.value.logo || '/logo.svg')

  async function loadBrand() {
    const pageConfigStore = usePageConfigStore()
    try {
      const data = await brandApi.getPublicBrand()
      if (data) {
        if (data.logo) brand.value.logo = data.logo
        if (data.name) brand.value.name = data.name
        if (data.loginTitle) brand.value.loginTitle = data.loginTitle
        if (data.loginSubtitle) brand.value.loginSubtitle = data.loginSubtitle
        if (data.defaultTheme) brand.value.defaultTheme = data.defaultTheme
      }
      // 首次访问或跟随系统默认的用户：应用管理员配置的默认主题（main.ts 在 await 后才挂载，无闪烁）
      if (pageConfigStore.firstVisit || pageConfigStore.config.followSystemTheme) {
        pageConfigStore.applySystemDefault(data?.defaultTheme)
      }
    } catch {
      // 接口不可用时保持默认值，保证登录页可用；首次访问回退既有行为（跟随用户沿用本地值）
      if (pageConfigStore.firstVisit) {
        pageConfigStore.finalizeFirstVisit()
      }
    } finally {
      loaded.value = true
      applyBrand()
    }
  }

  /** 将品牌应用到 document.title 与 favicon */
  function applyBrand() {
    document.title = `${brand.value.name} - ${brand.value.loginSubtitle}`
    const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
    if (icon) icon.href = logoUrl.value
  }

  return { brand, loaded, logoUrl, loadBrand, applyBrand }
})

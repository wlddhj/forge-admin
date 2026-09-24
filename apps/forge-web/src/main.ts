import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'nprogress/nprogress.css'
import '@fortawesome/fontawesome-free/css/all.min.css'
import {VxeUI} from "vxe-pc-ui";
import jQuery from 'jquery'
window.$ = window.jQuery = jQuery

import App from './App.vue'
import router from './router'
import './styles/index.scss'
import '@/styles/screen.scss'
import "@/assets/css/iconfont.css"
// import '@/assets/css/hiprint-style.css'
import { permission, role } from './directives/permission'

// 导入 vxe-table 插件
import { setupVxe } from './plugins/vxe'

// 导入 form-create 表单设计器插件
import { setupFormCreate } from './plugins/formCreate'
import {CACHE_KEY, useCache} from "@/hooks/web/useCache.ts";
import {isDark} from "@/utils/is.ts";
import { usePageConfigStore } from '@/stores/pageConfig'
import { useBrandStore } from '@/stores/brand'

const { wsCache } = useCache()
const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册权限指令
app.directive('permission', permission)
app.directive('role', role)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 注册 vxe-table
setupVxe(app)

// 注册 form-create 表单设计器
setupFormCreate(app)
// 根据浏览器当前主题设置系统主题色
const setDefaultTheme = () => {
  let isDarkTheme = wsCache.get(CACHE_KEY.IS_DARK)
  if (isDarkTheme === null) {
    isDarkTheme = isDark()
  }
  VxeUI.setTheme(isDarkTheme ? 'dark' : 'light')
}
setDefaultTheme()

// 显式实例化 pageConfig store，保证启动时立即应用 palette/layout/style 与 theme
// 避免依赖 BasicLayout 渲染时的惰性初始化导致主题闪烁
const pageConfigStore = usePageConfigStore()
pageConfigStore.applyPalette(pageConfigStore.config.palette)
pageConfigStore.applyLayout(pageConfigStore.config.layout)
pageConfigStore.applyStyle(pageConfigStore.config.style)
pageConfigStore.applyTheme(pageConfigStore.config.theme)

// 加载品牌配置（document.title/favicon 在数据返回后应用，接口异常回退 .env 默认值）
const brandStore = useBrandStore()
const loadBrandTask = brandStore.loadBrand()

if (pageConfigStore.firstVisit || pageConfigStore.config.followSystemTheme) {
  // 首次访问或跟随系统默认主题：等默认主题返回并应用后再挂载，避免主题闪烁；
  // 接口挂起时 5s 兜底挂载（axios 全局 timeout 30s 过长，不能让首屏白屏等它）
  let mounted = false
  const mountOnce = () => {
    if (mounted) return
    mounted = true
    app.mount('#app')
  }
  loadBrandTask.finally(mountOnce)
  setTimeout(mountOnce, 5000)
} else {
  // 已自定义主题的用户：不阻塞挂载
  app.mount('#app')
}

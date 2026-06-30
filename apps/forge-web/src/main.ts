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

import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { permission, role } from './directives/permission'

// 导入 vxe-table 插件
import { setupVxe } from './plugins/vxe'

// 导入 form-create 表单设计器插件
import { setupFormCreate } from './plugins/formCreate'
import {CACHE_KEY, useCache} from "@/hooks/web/useCache.ts";
import {isDark} from "@/utils/is.ts";

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
app.mount('#app')

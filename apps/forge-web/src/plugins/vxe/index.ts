import type { App } from 'vue'
import {
  VxeUI,
  VxeButton,
  VxeCheckbox,
  VxeIcon,
  VxeInput,
  VxeModal,
  VxeNumberInput,
  VxePrint,
  VxeRadio,
  VxeRadioButton,
  VxeRadioGroup,
  VxeSelect,
  VxeTooltip,
  VxeUpload
} from 'vxe-pc-ui'

import {
  VxeTable,
  VxeColumn,
  VxeColgroup,
  VxeToolbar
} from 'vxe-table'

import 'vxe-table/lib/style.css'
import 'vxe-pc-ui/lib/style.css'

// 导入中文语言包
import zhCN from 'vxe-pc-ui/lib/language/zh-CN'

// 导入全局配置
import './vxe-table-config'

// 设置中文语言
VxeUI.setI18n('zh-CN', zhCN)
VxeUI.setLanguage('zh-CN')

// 需要全局注册的组件
const components = [
  VxeTable,
  VxeColumn,
  VxeColgroup,
  VxeToolbar,
  VxeTooltip,
  VxeButton,
  VxeNumberInput,
  VxeRadioGroup,
  VxeModal,
  VxeInput,
  VxeRadio,
  VxePrint,
  VxeSelect,
  VxeCheckbox,
  VxeUpload,
  VxeRadioButton,
  VxeIcon
]

/**
 * 注册 vxe-table 插件
 */
export function setupVxe(app: App<Element>) {
  components.forEach((component) => {
    app.component(component.name, component)
  })
}

export { VxeUI }
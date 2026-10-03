import { reactive } from 'vue'

// URL query 注入的公共参数（如 accountSetId），FORGE 数据源取数时与组件静态 forgeParams 合并，URL 优先于组件静态值
const screenParams = reactive<Record<string, string>>({})

const RESERVED_KEYS = new Set(['code', 'token'])

export function initScreenParams(queryStr: string) {
  new URLSearchParams(queryStr).forEach((value, key) => {
    if (!RESERVED_KEYS.has(key) && value !== '') {
      screenParams[key] = value
    }
  })
}

export function getScreenParams(): Record<string, string> {
  return screenParams
}

import { ref } from 'vue'
import request from '@/utils/request'

/**
 * 多租户配置 composable
 *
 * 前端启动时调用 /system/tenant/public/enabled 一次
 * 登录页、头部 TenantSwitcher 等根据此值决定是否显示租户相关 UI
 *
 * 注意:不缓存到 localStorage,因为后端配置可能动态变化
 *      接口未返回前 enabled 默认 false(避免关闭多租户时短暂闪现)
 */

// 全局单例,默认 false(关闭多租户场景的安全默认值)
const _enabled = ref<boolean>(false)
let _loaded = false
let _loading: Promise<void> | null = null

export function useTenantConfig() {
  const loadIfNeeded = async (): Promise<void> => {
    if (_loaded) return
    if (_loading) return _loading
    _loading = (async () => {
      try {
        const res: any = await request.get('/system/tenant/public/enabled')
        _enabled.value = res?.data?.enabled
      } catch {
        // 失败保持 false
      } finally {
        _loaded = true
        _loading = null
      }
    })()
    return _loading
  }

  return {
    enabled: _enabled,
    loadIfNeeded,
    refresh: async () => {
      _loaded = false
      await loadIfNeeded()
    }
  }
}


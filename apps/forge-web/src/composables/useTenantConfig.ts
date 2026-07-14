import { ref } from 'vue'
import request from '@/utils/request'

/**
 * 多租户配置 composable
 *
 * 前端启动时调用 /system/tenant/public/enabled 一次,缓存到本地 storage
 * 登录页、头部 TenantSwitcher 等根据此值决定是否显示租户相关 UI
 */
const TENANT_ENABLED_KEY = 'forge.tenant.enabled'

function readCached(): boolean {
  const v = localStorage.getItem(TENANT_ENABLED_KEY)
  if (v === 'true') return true
  if (v === 'false') return false
  return true
}

const _enabled = ref<boolean>(readCached())
let _loaded = false
let _loading: Promise<void> | null = null

export function useTenantConfig() {
  const loadIfNeeded = async (): Promise<void> => {
    if (_loaded) return
    if (_loading) return _loading
    _loading = (async () => {
      try {
        const res: any = await request.get('/system/tenant/public/enabled')
        const enabled = !!res?.data?.enabled
        _enabled.value = enabled
        localStorage.setItem(TENANT_ENABLED_KEY, String(enabled))
      } catch {
        // 失败用本地缓存值
      } finally {
        _loaded = true
        _loading = null
      }
    })()
    return _loading
  }

  return {
    enabled: _enabled,
    refresh: async () => {
      _loaded = false
      await loadIfNeeded()
    }
  }
}


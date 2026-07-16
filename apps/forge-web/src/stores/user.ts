import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { login, getUserInfo, getUserMenus, logout, heartbeat } from '@/api/auth'
import type { LoginRequest, UserInfo } from '@/types/auth'
import type { MenuTree } from '@/types/system'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { resetExpiredState, triggerRefresh } from '@/utils/request'
import router, { resetRouter } from '@/router'

// 心跳间隔：2分钟
const HEARTBEAT_INTERVAL = 2 * 60 * 1000
// Token 过期前提前刷新的阈值：5分钟
const TOKEN_REFRESH_THRESHOLD = 5 * 60 * 1000

// 解码 JWT 获取过期时间（毫秒时间戳）
const getTokenExp = (token: string): number | null => {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1]))
    return payload.exp ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

// 解码 JWT 获取 tenantId（后端在 JWT claim 中写入 tenantId）
const getTokenTenantId = (token: string): number | null => {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1]))
    if (payload.tenantId === undefined || payload.tenantId === null) return null
    const num = Number(payload.tenantId)
    return Number.isNaN(num) ? null : num
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const refreshTokenValue = ref<string>(localStorage.getItem('refreshToken') || '')
  const tokenExpireTime = ref<number>(Number(localStorage.getItem('tokenExpireTime')) || 0)
  const userInfo = ref<UserInfo | null>(null)
  const menus = ref<MenuTree[]>([])
  const tenantId = ref<number | null>(
    (() => {
      const stored = localStorage.getItem('tenantId')
      if (stored === null) return null
      const num = Number(stored)
      return Number.isNaN(num) ? null : num
    })()
  )
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let isRefreshingToken = false

  const setTokenExpireTime = (expiresIn: number | string) => {
    // expiresIn 单位是毫秒（与后端一致），后端可能返回字符串
    const ms = Number(expiresIn)
    const expireAt = Date.now() + ms
    tokenExpireTime.value = expireAt
    localStorage.setItem('tokenExpireTime', String(expireAt))
  }

  // 设置租户ID（持久化到 localStorage）
  const setTenantId = (id: number | null) => {
    tenantId.value = id
    if (id === null) {
      localStorage.removeItem('tenantId')
    } else {
      localStorage.setItem('tenantId', String(id))
    }
  }

  // 获取 token 过期时间（优先用存储值，后备 JWT 解码）
  const getExpireTime = (): number => {
    if (tokenExpireTime.value > 0) return tokenExpireTime.value
    if (token.value) {
      const jwtExp = getTokenExp(token.value)
      if (jwtExp) {
        // 回填存储值
        tokenExpireTime.value = jwtExp
        localStorage.setItem('tokenExpireTime', String(jwtExp))
        return jwtExp
      }
    }
    return 0
  }

  // Token 是否即将过期（剩余时间 < 阈值）
  const isTokenExpiringSoon = () => {
    const expireTime = getExpireTime()
    return expireTime > 0 && Date.now() > expireTime - TOKEN_REFRESH_THRESHOLD
  }

  // 启动心跳定时器
  const startHeartbeat = () => {
    stopHeartbeat()
    heartbeatTimer = setInterval(async () => {
      if (!token.value) return

      // Token 即将过期，主动刷新
      if (isTokenExpiringSoon() && !isRefreshingToken) {
        isRefreshingToken = true
        try {
          await triggerRefresh()
        } catch {
          // 刷新失败，执行登出
          ElMessage.error('登录已过期，请重新登录')
          const permissionStore = usePermissionStore()
          const tabsStore = useTabsStore()
          logoutAction().finally(() => {
            permissionStore.resetRoutes()
            tabsStore.clearAllTabs()
            resetRouter()
            router.push('/login')
          })
          return
        } finally {
          isRefreshingToken = false
        }
      }

      try {
        await heartbeat()
      } catch {
        // 心跳失败静默忽略
      }
    }, HEARTBEAT_INTERVAL)
  }

  // 停止心跳定时器
  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  // 登录
  const loginAction = async (loginForm: LoginRequest) => {
    const res = await login(loginForm)
    // 首次登录强制改密：后端不返回 token，跳过 token 持久化与心跳，由登录页弹改密弹窗
    if (res.needChangePassword) {
      return res
    }
    token.value = res.accessToken
    refreshTokenValue.value = res.refreshToken
    localStorage.setItem('token', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    setTokenExpireTime(res.expiresIn)
    // 直接使用后端 LoginResponse.tenantId（后端已明确返回，无需解码 JWT）
    if (res.tenantId !== undefined && res.tenantId !== null) {
      setTenantId(Number(res.tenantId))
    } else {
      // 兼容旧后端：从 JWT claim 解码
      const fallback = getTokenTenantId(res.accessToken)
      if (fallback !== null) setTenantId(fallback)
    }
    resetExpiredState()
    // 登录成功后启动心跳
    startHeartbeat()
    return res
  }

  // 更新 Token（由 request.ts 调用）
  const updateToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
    // token 刷新后 tenantId 可能变化，重新解码同步
    const jwtTenantId = getTokenTenantId(newToken)
    if (jwtTenantId !== null && jwtTenantId !== tenantId.value) {
      setTenantId(jwtTenantId)
    }
  }

  // 更新 Refresh Token
  const updateRefreshToken = (newRefreshToken: string) => {
    refreshTokenValue.value = newRefreshToken
    localStorage.setItem('refreshToken', newRefreshToken)
  }

  // 获取用户信息
  const getUserInfoAction = async () => {
    const res = await getUserInfo()
    userInfo.value = res
    // 如果响应中携带 tenantId，回填到 store（后端 UserInfoResponse 未返回时保持 JWT 解码值）
    if (res.tenantId !== undefined && res.tenantId !== null) {
      setTenantId(Number(res.tenantId))
    }
    return res
  }

  // 获取用户菜单
  const getMenusAction = async () => {
    const res = await getUserMenus()
    menus.value = res
    return res
  }

  // 退出登录
  const logoutAction = async () => {
    try {
      await logout(refreshTokenValue.value)
    } catch {
      // 忽略 logout API 错误，直接清除本地状态
    } finally {
      stopHeartbeat()
      token.value = ''
      refreshTokenValue.value = ''
      tokenExpireTime.value = 0
      userInfo.value = null
      menus.value = []
      setTenantId(null)
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('tokenExpireTime')
    }
  }

  // 更新用户信息
  const updateUserInfo = (info: Partial<UserInfo>) => {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...info }
    }
    if (info.tenantId !== undefined && info.tenantId !== null) {
      setTenantId(Number(info.tenantId))
    }
  }

  // 如果已有 token，启动心跳
  if (token.value) {
    startHeartbeat()
  }

  return {
    token,
    refreshTokenValue,
    tokenExpireTime,
    userInfo,
    menus,
    tenantId,
    loginAction,
    updateToken,
    updateRefreshToken,
    setTokenExpireTime,
    setTenantId,
    getUserInfoAction,
    getMenusAction,
    logoutAction,
    updateUserInfo,
    startHeartbeat,
    stopHeartbeat
  }
})

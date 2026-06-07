import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { login, getUserInfo, getUserMenus, logout, heartbeat } from '@/api/auth'
import type { LoginRequest, UserInfo } from '@/types/auth'
import type { MenuTree } from '@/types/system'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { resetExpiredState } from '@/utils/request'
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

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const refreshTokenValue = ref<string>(localStorage.getItem('refreshToken') || '')
  const tokenExpireTime = ref<number>(Number(localStorage.getItem('tokenExpireTime')) || 0)
  const userInfo = ref<UserInfo | null>(null)
  const menus = ref<MenuTree[]>([])
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let isRefreshingToken = false

  const setTokenExpireTime = (expiresIn: number | string) => {
    // expiresIn 单位是毫秒（与后端一致），后端可能返回字符串
    const ms = Number(expiresIn)
    const expireAt = Date.now() + ms
    tokenExpireTime.value = expireAt
    localStorage.setItem('tokenExpireTime', String(expireAt))
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

      // Token 即将过期，主动刷新（用原始 axios 避免拦截器循环）
      if (isTokenExpiringSoon() && !isRefreshingToken) {
        isRefreshingToken = true
        try {
          await directRefreshToken()
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

  // 直接调用刷新接口（绕过请求拦截器，避免 token 过期时的循环依赖）
  const directRefreshToken = async () => {
    if (!refreshTokenValue.value) {
      throw new Error('No refresh token available')
    }

    const res = await axios.post('/api/auth/refresh', { refreshToken: refreshTokenValue.value })
    const data = res.data?.data
    if (res.data?.code !== 200 || !data) {
      throw new Error(res.data?.message || 'Token 刷新失败')
    }

    token.value = data.accessToken
    refreshTokenValue.value = data.refreshToken
    localStorage.setItem('token', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    if (data.expiresIn) {
      setTokenExpireTime(data.expiresIn)
    }
    return data
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
    token.value = res.accessToken
    refreshTokenValue.value = res.refreshToken
    localStorage.setItem('token', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    setTokenExpireTime(res.expiresIn)
    resetExpiredState()
    // 登录成功后启动心跳
    startHeartbeat()
    return res
  }

  // 更新 Token（由 request.ts 调用）
  const updateToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
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
  }

  // 如果已有 token，启动心跳
  if (token.value) {
    startHeartbeat()
  }

  return {
    token,
    refreshTokenValue,
    userInfo,
    menus,
    loginAction,
    updateToken,
    updateRefreshToken,
    setTokenExpireTime,
    getUserInfoAction,
    getMenusAction,
    logoutAction,
    updateUserInfo,
    startHeartbeat,
    stopHeartbeat
  }
})

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login, getUserInfo, getUserMenus, logout, refreshToken, heartbeat } from '@/api/auth'
import type { LoginRequest, UserInfo } from '@/types/auth'
import type { MenuTree } from '@/types/system'

// 心跳间隔：2分钟
const HEARTBEAT_INTERVAL = 2 * 60 * 1000

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const refreshTokenValue = ref<string>(localStorage.getItem('refreshToken') || '')
  const userInfo = ref<UserInfo | null>(null)
  const menus = ref<MenuTree[]>([])
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null

  // 启动心跳定时器
  const startHeartbeat = () => {
    stopHeartbeat()
    heartbeatTimer = setInterval(async () => {
      if (token.value) {
        try {
          await heartbeat()
        } catch {
          // 忽略心跳失败
        }
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
    token.value = res.accessToken
    refreshTokenValue.value = res.refreshToken
    localStorage.setItem('token', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    // 登录成功后启动心跳
    startHeartbeat()
    return res
  }

  // 刷新 Token
  const refreshTokenAction = async () => {
    if (!refreshTokenValue.value) {
      throw new Error('No refresh token available')
    }

    const res = await refreshToken({ refreshToken: refreshTokenValue.value })
    token.value = res.accessToken
    refreshTokenValue.value = res.refreshToken
    localStorage.setItem('token', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
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
      // 停止心跳
      stopHeartbeat()
      token.value = ''
      refreshTokenValue.value = ''
      userInfo.value = null
      menus.value = []
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
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
    refreshTokenAction,
    updateToken,
    updateRefreshToken,
    getUserInfoAction,
    getMenusAction,
    logoutAction,
    updateUserInfo,
    startHeartbeat,
    stopHeartbeat
  }
})

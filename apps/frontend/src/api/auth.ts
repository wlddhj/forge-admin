import request from '@/utils/request'
import type { LoginRequest, LoginResponse, UserInfo, RefreshTokenRequest } from '@/types/auth'
import type { MenuTree } from '@/types/system'

// 登录
export function login(data: LoginRequest) {
  return request.post<LoginResponse>('/auth/login', data).then(res => res.data)
}

// 刷新 Token
export function refreshToken(data: RefreshTokenRequest) {
  return request.post<LoginResponse>('/auth/refresh', data).then(res => res.data)
}

// 获取用户信息
export function getUserInfo() {
  return request.get<UserInfo>('/auth/userinfo').then(res => res.data)
}

// 获取用户菜单
export function getUserMenus() {
  return request.get<MenuTree[]>('/auth/menus').then(res => res.data)
}

// 退出登录
export function logout(refreshToken?: string) {
  const headers: Record<string, string> = {}
  if (refreshToken) {
    headers['X-Refresh-Token'] = refreshToken
  }
  return request.post('/auth/logout', undefined, { headers })
}

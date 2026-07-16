import request from '@/utils/request'
import type { LoginRequest, LoginResponse, UserInfo, RefreshTokenRequest, CaptchaResponse } from '@/types/auth'
import type { MenuTree } from '@/types/system'

// 登录
export function login(data: LoginRequest) {
  return request.post<LoginResponse>('/auth/login', data).then(res => res.data)
}

// 首次登录强制改密（不走 token 鉴权）
export interface FirstLoginChangePasswordRequest {
  username: string
  tenantCode?: string
  oldPassword: string
  newPassword: string
}

export function changePasswordFirstLogin(data: FirstLoginChangePasswordRequest) {
  return request.post('/auth/change-password-first-login', data)
}

// 获取验证码
export function getCaptcha() {
  return request.get<CaptchaResponse>('/auth/captcha').then(res => res.data)
}

// 刷新 Token（静默模式，避免 401 时触发拦截器的自动刷新循环）
export function refreshToken(data: RefreshTokenRequest) {
  return request.post<LoginResponse>('/auth/refresh', data, { silent: true } as any).then(res => res.data)
}

// 获取用户信息
export function getUserInfo() {
  return request.get<UserInfo>('/auth/userinfo').then(res => res.data)
}

// 获取用户菜单
export function getUserMenus() {
  return request.get<MenuTree[]>('/auth/menus').then(res => res.data)
}

// 心跳接口（静默模式，401 不触发自动刷新/登出）
export function heartbeat() {
  return request.post('/auth/heartbeat', undefined, { silent: true } as any)
}

// 退出登录
export function logout(refreshToken?: string) {
  const headers: Record<string, string> = {}
  if (refreshToken) {
    headers['X-Refresh-Token'] = refreshToken
  }
  return request.post('/auth/logout', undefined, { headers })
}

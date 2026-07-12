// 登录请求
export interface LoginRequest {
  tenantCode?: string
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
}

// 登录响应
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
  needChangePassword?: boolean
  message?: string
  passwordExpireDays?: number
  passwordExpired?: boolean
}

// 验证码响应
export interface CaptchaResponse {
  captchaId: string
  captchaImage: string
}

// 刷新 Token 请求
export interface RefreshTokenRequest {
  refreshToken: string
}

// 用户信息
export interface UserInfo {
  userId: number
  username: string
  nickname: string
  avatar: string
  deptId: number
  deptName: string
  roles: string[]
  permissions: string[]
  passwordExpireDays?: number
  passwordExpired?: boolean
}

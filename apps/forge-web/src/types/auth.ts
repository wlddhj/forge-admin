// 登录请求
export interface LoginRequest {
  tenantCode: string
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
  /** 租户ID（后端 LoginResponse 直接返回，前端无需解码 JWT） */
  tenantId?: number | null
  /** 租户标识（登录时输入的 tenantCode 原样回显） */
  tenantCode?: string
  /** 租户名称（用于前端展示） */
  tenantName?: string
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
  tenantId?: number | null
}

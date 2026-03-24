// 登录请求
export interface LoginRequest {
  username: string
  password: string
}

// 登录响应
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
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
}

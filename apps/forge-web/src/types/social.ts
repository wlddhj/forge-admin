/** 社交账号绑定信息 */
export interface SocialBinding {
  id: number
  source: string
  sourceName: string
  nickname: string
  avatar: string
  bindTime: string
}

/** 社交账号绑定请求 */
export interface SocialBindRequest {
  tempToken: string
}

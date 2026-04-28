/** OAuth2 客户端信息 */
export interface OAuth2Client {
  id: string
  clientId: string
  clientName: string
  clientIdIssuedAt: string
  redirectUris: string[]
  authorizationGrantTypes: string[]
  clientAuthenticationMethods: string[]
  scopes: string[]
}

/** OAuth2 客户端创建请求 */
export interface ClientCreateRequest {
  clientId: string
  clientName: string
  redirectUris: string[]
  authorizationGrantTypes: string[]
  clientAuthenticationMethods: string[]
  scopes: string[]
  accessTokenTimeToLive?: number
  refreshTokenTimeToLive?: number
}

/** OAuth2 客户端更新请求 */
export interface ClientUpdateRequest {
  id: string
  clientName?: string
  redirectUris?: string[]
  authorizationGrantTypes?: string[]
  clientAuthenticationMethods?: string[]
  scopes?: string[]
  accessTokenTimeToLive?: number
  refreshTokenTimeToLive?: number
}

/** OAuth2 客户端查询参数 */
export interface ClientQueryRequest {
  clientId?: string
  clientName?: string
  pageNum?: number
  pageSize?: number
}

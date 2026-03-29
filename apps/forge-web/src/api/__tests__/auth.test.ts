import { describe, it, expect, vi, beforeEach } from 'vitest'

// mock request 模块
vi.mock('@/utils/request', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn()
  }
}))

import request from '@/utils/request'
import { login, refreshToken, getUserInfo, getUserMenus, heartbeat, logout } from '../auth'

describe('auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('login 应调用 POST /auth/login', async () => {
    const mockResponse = {
      data: {
        accessToken: 'token',
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        refreshExpiresIn: 86400
      }
    }
    vi.mocked(request.post).mockResolvedValue(mockResponse)

    const result = await login({ username: 'admin', password: '123' })
    expect(request.post).toHaveBeenCalledWith('/auth/login', {
      username: 'admin',
      password: '123'
    })
    expect(result).toEqual(mockResponse.data)
  })

  it('refreshToken 应调用 POST /auth/refresh', async () => {
    const mockResponse = { data: { accessToken: 'new', refreshToken: 'newRefresh' } }
    vi.mocked(request.post).mockResolvedValue(mockResponse)

    const result = await refreshToken({ refreshToken: 'old-refresh' })
    expect(request.post).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'old-refresh' })
    expect(result).toEqual(mockResponse.data)
  })

  it('getUserInfo 应调用 GET /auth/userinfo', async () => {
    const mockResponse = {
      data: { userId: 1, username: 'admin', roles: ['admin'] }
    }
    vi.mocked(request.get).mockResolvedValue(mockResponse)

    const result = await getUserInfo()
    expect(request.get).toHaveBeenCalledWith('/auth/userinfo')
    expect(result).toEqual(mockResponse.data)
  })

  it('getUserMenus 应调用 GET /auth/menus', async () => {
    const mockResponse = { data: [{ id: 1, name: '系统管理' }] }
    vi.mocked(request.get).mockResolvedValue(mockResponse)

    const result = await getUserMenus()
    expect(request.get).toHaveBeenCalledWith('/auth/menus')
    expect(result).toEqual(mockResponse.data)
  })

  it('heartbeat 应调用 POST /auth/heartbeat', async () => {
    vi.mocked(request.post).mockResolvedValue({})
    await heartbeat()
    expect(request.post).toHaveBeenCalledWith('/auth/heartbeat')
  })

  it('logout 应带 X-Refresh-Token header', async () => {
    vi.mocked(request.post).mockResolvedValue({})
    await logout('my-refresh-token')
    expect(request.post).toHaveBeenCalledWith('/auth/logout', undefined, {
      headers: { 'X-Refresh-Token': 'my-refresh-token' }
    })
  })

  it('logout 不传 refreshToken 时不应带 header', async () => {
    vi.mocked(request.post).mockResolvedValue({})
    await logout()
    expect(request.post).toHaveBeenCalledWith('/auth/logout', undefined, {
      headers: {}
    })
  })
})

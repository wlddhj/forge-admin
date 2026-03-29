import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../user'

// mock API
vi.mock('@/api/auth', () => ({
  login: vi.fn().mockResolvedValue({
    accessToken: 'test-access-token',
    refreshToken: 'test-refresh-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
    refreshExpiresIn: 86400
  }),
  getUserInfo: vi.fn().mockResolvedValue({
    userId: 1,
    username: 'admin',
    nickname: '管理员',
    avatar: '',
    deptId: 1,
    deptName: '总部',
    roles: ['admin'],
    permissions: ['system:user:list']
  }),
  getUserMenus: vi.fn().mockResolvedValue([
    { id: 1, name: '系统管理', path: '/system', children: [] }
  ]),
  logout: vi.fn().mockResolvedValue({}),
  refreshToken: vi.fn().mockResolvedValue({
    accessToken: 'new-access-token',
    refreshToken: 'new-refresh-token'
  }),
  heartbeat: vi.fn().mockResolvedValue({})
}))

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('初始状态应为空', () => {
    const store = useUserStore()
    expect(store.token).toBe('')
    expect(store.refreshTokenValue).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.menus).toEqual([])
  })

  it('loginAction 应存储 token 到 state 和 localStorage', async () => {
    const store = useUserStore()
    await store.loginAction({ username: 'admin', password: 'password' })

    expect(store.token).toBe('test-access-token')
    expect(store.refreshTokenValue).toBe('test-refresh-token')
    expect(localStorage.getItem('token')).toBe('test-access-token')
    expect(localStorage.getItem('refreshToken')).toBe('test-refresh-token')
  })

  it('getUserInfoAction 应更新 userInfo', async () => {
    const store = useUserStore()
    const info = await store.getUserInfoAction()

    expect(info.username).toBe('admin')
    expect(store.userInfo?.username).toBe('admin')
    expect(store.userInfo?.roles).toContain('admin')
  })

  it('getMenusAction 应更新 menus', async () => {
    const store = useUserStore()
    const menus = await store.getMenusAction()

    expect(menus).toHaveLength(1)
    expect(store.menus).toHaveLength(1)
  })

  it('logoutAction 应清除所有状态', async () => {
    const store = useUserStore()
    await store.loginAction({ username: 'admin', password: 'password' })
    expect(store.token).toBeTruthy()

    await store.logoutAction()
    expect(store.token).toBe('')
    expect(store.refreshTokenValue).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.menus).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('updateToken 应更新 token', () => {
    const store = useUserStore()
    store.updateToken('new-token')
    expect(store.token).toBe('new-token')
    expect(localStorage.getItem('token')).toBe('new-token')
  })

  it('updateUserInfo 应合并更新用户信息', async () => {
    const store = useUserStore()
    await store.getUserInfoAction()

    store.updateUserInfo({ nickname: '超级管理员' })
    expect(store.userInfo?.nickname).toBe('超级管理员')
    expect(store.userInfo?.username).toBe('admin')
  })
})

import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../user'
import { getUserInfo } from '@/api/auth'

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
    await store.loginAction({ tenantCode: 'default', username: 'admin', password: 'password' })

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
    await store.loginAction({ tenantCode: 'default', username: 'admin', password: 'password' })
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

  // 构造带 tenantId claim 的伪 JWT（payload 为 base64 的 JSON）
  const fakeJwtWithTenant = (tenantId: number) =>
    `header.${btoa(JSON.stringify({ tenantId }))}.signature`

  describe('平台超管租户切换上下文', () => {
    const adminInfo = {
      userId: 1,
      username: 'admin',
      nickname: '管理员',
      avatar: '',
      deptId: 1,
      deptName: '总部',
      roles: ['admin'],
      permissions: ['system:tenant:list'],
      accountType: 2,
      tenantId: 1
    }

    it('getUserInfoAction 不应覆盖超管已切换的租户', async () => {
      vi.mocked(getUserInfo).mockResolvedValueOnce(adminInfo)
      const store = useUserStore()
      // 模拟已通过切换器切到租户 2
      store.setTenantId(2)

      await store.getUserInfoAction()

      // userinfo 返回的 tenantId=1（admin 所属默认租户）不得回写
      expect(store.tenantId).toBe(2)
      expect(localStorage.getItem('tenantId')).toBe('2')
    })

    it('updateToken 不应用 JWT claim 覆盖超管已切换的租户', async () => {
      vi.mocked(getUserInfo).mockResolvedValueOnce(adminInfo)
      const store = useUserStore()
      await store.getUserInfoAction()
      store.setTenantId(2)

      // 刷新后的新 token 里 tenantId claim 仍是 1（admin 所属租户）
      store.updateToken(fakeJwtWithTenant(1))

      expect(store.tenantId).toBe(2)
      expect(localStorage.getItem('tenantId')).toBe('2')
    })

    it('普通用户 userinfo 返回 tenantId 仍正常回填', async () => {
      vi.mocked(getUserInfo).mockResolvedValueOnce({
        userId: 2,
        username: 'zhangsan',
        nickname: '张三',
        avatar: '',
        deptId: 2,
        deptName: '研发部',
        roles: ['user'],
        permissions: [],
        accountType: 0,
        tenantId: 3
      })
      const store = useUserStore()

      await store.getUserInfoAction()

      expect(store.tenantId).toBe(3)
    })

    it('普通用户 updateToken 仍按 JWT 同步 tenantId', async () => {
      const store = useUserStore()
      store.setTenantId(2)

      store.updateToken(fakeJwtWithTenant(3))

      expect(store.tenantId).toBe(3)
    })
  })
})

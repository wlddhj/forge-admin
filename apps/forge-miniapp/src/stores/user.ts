import { defineStore } from 'pinia'

interface UserState {
  accessToken: string | null
  refreshToken: string | null
  userInfo: {
    id: number
    nickname: string
    avatar: string
    phone: string | null
    phoneVerified: number
  } | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    accessToken: null,
    refreshToken: null,
    userInfo: null
  }),
  actions: {
    setTokens(access: string, refresh: string) {
      this.accessToken = access
      this.refreshToken = refresh
    },
    setUserInfo(info: UserState['userInfo']) {
      this.userInfo = info
    },
    clear() {
      this.accessToken = null
      this.refreshToken = null
      this.userInfo = null
    },
    init() {
      // 启动时验证 token 有效性（暂时跳过，待 api 封装完成后实现）
    }
  },
  persist: {
    storage: {
      getItem: (key) => uni.getStorageSync(key),
      setItem: (key, value) => uni.setStorageSync(key, value)
    }
  }
})
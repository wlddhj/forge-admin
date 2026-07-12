import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import router, { resetRouter } from '@/router'

// 响应数据结构
export interface Result<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

// 分页响应
export interface PageResult<T = any> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// 解码 JWT 获取过期时间（毫秒时间戳），失败返回 null
const getTokenExp = (token: string): number | null => {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1]))
    return payload.exp ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

// 检查 token 是否已过期
const isTokenExpired = (): boolean => {
  const userStore = useUserStore()
  if (!userStore.token) return false

  // 优先用存储的过期时间
  const storedExpireTime = Number(localStorage.getItem('tokenExpireTime')) || 0
  if (storedExpireTime > 0) {
    return Date.now() > storedExpireTime
  }

  // 后备：解码 JWT
  const jwtExp = getTokenExp(userStore.token)
  if (jwtExp) {
    return Date.now() > jwtExp
  }

  return false
}

// 模块级状态
let isHandlingExpired = false
// 共享的刷新 Promise，所有并发请求等待同一个
let refreshPromise: Promise<string> | null = null

// 执行登出并跳转登录页
const handleLogout = (errorMessage?: string) => {
  if (isHandlingExpired) return
  isHandlingExpired = true

  // 关闭其他所有消息提示，避免并发请求各自弹窗造成视觉污染
  ElMessage.closeAll()
  ElMessage.error(errorMessage || '登录已过期，请重新登录')

  const userStore = useUserStore()
  const permissionStore = usePermissionStore()
  const tabsStore = useTabsStore()
  userStore.logoutAction().finally(() => {
    permissionStore.resetRoutes()
    tabsStore.clearAllTabs()
    resetRouter()
    router.push('/login')
  })
}

// 获取共享的刷新 Promise
const getRefreshPromise = (): Promise<string> => {
  if (refreshPromise) return refreshPromise

  const userStore = useUserStore()
  const refreshTokenValue = userStore.refreshTokenValue

  if (!refreshTokenValue) {
    refreshPromise = Promise.reject(new Error('No refresh token'))
    handleLogout('未登录或登录已过期')
    return refreshPromise
  }

  refreshPromise = axios
    .post<Result<any>>('/admin-api/auth/refresh', { refreshToken: refreshTokenValue })
    .then((response) => {
      const res = response.data
      if (res.code === 200 && res.data) {
        userStore.updateToken(res.data.accessToken)
        userStore.updateRefreshToken(res.data.refreshToken)
        if (res.data.expiresIn) {
          userStore.setTokenExpireTime(res.data.expiresIn)
        }
        return res.data.accessToken
      }
      throw new Error(res.message || 'Token 刷新失败')
    })
    .catch((err) => {
      const message = err.name === 'AbortError' || err.name === 'CanceledError'
        ? 'Token 刷新超时'
        : '登录已过期，请重新登录'
      handleLogout(message)
      throw err
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/admin-api',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const url = config.url || ''

    // 公开接口白名单：登录、刷新、验证码 —— 始终放行，不检查过期状态
    const isPublicApi = url.includes('/auth/login')
      || url.includes('/auth/refresh')
      || url.includes('/auth/captcha')

    if (isPublicApi) {
      return config
    }

    // 已在处理过期状态，拒绝其他请求（避免并发刷新）
    if (isHandlingExpired) {
      return Promise.reject(new Error('登录已过期'))
    }

    const userStore = useUserStore()

    // 注入租户ID请求头（后端通过 X-Tenant-Id 识别租户上下文）
    if (userStore.tenantId !== null && userStore.tenantId !== undefined) {
      config.headers['X-Tenant-Id'] = String(userStore.tenantId)
    }

    if (!userStore.token) return config

    // token 未过期，正常添加 header
    if (!isTokenExpired()) {
      config.headers.Authorization = `Bearer ${userStore.token}`
      return config
    }

    // token 已过期，走共享刷新逻辑
    return getRefreshPromise()
      .then((newToken) => {
        config.headers.Authorization = `Bearer ${newToken}`
        return config
      })
      .catch(() => {
        return Promise.reject(new Error('登录已过期'))
      })
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    // blob 响应直接返回数据，不做统一处理
    if (response.config.responseType === 'blob') {
      return response.data as any
    }

    const res = response.data
    const isSilent = (response.config as any).silent

    // code 不是 200 则为错误
    if (res.code !== 200) {
      if (isSilent || isHandlingExpired) {
        return Promise.reject(new Error(res.message || '请求失败'))
      }
      // 401: 未登录或 Token 过期（兜底，正常情况下请求拦截器已处理）
      if (res.code === 401) {
        return getRefreshPromise()
          .then((newToken) => {
            if (response.config) {
              response.config.headers.Authorization = `Bearer ${newToken}`
              return service.request(response.config)
            }
            return Promise.reject(new Error('刷新失败'))
          })
          .catch(() => {
            return Promise.reject(new Error('登录已过期'))
          })
      }
      ElMessage.error(res.message || '请求失败')

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    // 统一将分页结果中的 total 转为数字类型
    if (res.data && res.data.total !== undefined) {
      res.data.total = Number(res.data.total)
    }

    return res as any
  },
  (error) => {
    const isSilent = (error.config as any)?.silent

    // 401 错误：尝试用共享刷新 Promise 兜底
    if (error.response?.status === 401) {
      if (isSilent || isHandlingExpired) {
        return Promise.reject(error)
      }
      return getRefreshPromise()
        .then((newToken) => {
          if (error.config) {
            error.config.headers.Authorization = `Bearer ${newToken}`
            return service.request(error.config)
          }
          return Promise.reject(error)
        })
        .catch(() => {
          return Promise.reject(error)
        })
    }

    // 正在处理过期状态时，不弹额外错误（由 handleLogout 统一提示）
    if (!isSilent && !isHandlingExpired) {
      let message = '请求失败'
      if (error.response) {
        switch (error.response.status) {
          case 403:
            message = '没有相关权限'
            break
          case 404:
            message = '资源不存在'
            break
          case 500:
            message = '服务器错误'
            break
          default:
            message = error.response.data?.message || '请求失败'
        }
      } else if (error.message.includes('timeout')) {
        message = '请求超时'
      } else if (error.message.includes('Network')) {
        message = '网络错误'
      }

      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

// 请求方法
export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.get(url, config)
  },

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.post(url, data, config)
  },

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.put(url, data, config)
  },

  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.delete(url, config)
  }
}

// 重置登录过期状态（登录成功后调用）
export const resetExpiredState = () => {
  isHandlingExpired = false
  refreshPromise = null
}

// 获取共享刷新 Promise（心跳等外部调用方使用）
export const getSharedRefreshPromise = (): Promise<string> | null => {
  return refreshPromise
}

// 触发共享刷新（心跳等外部调用方使用）
export const triggerRefresh = (): Promise<string> => {
  return getRefreshPromise()
}

export default service

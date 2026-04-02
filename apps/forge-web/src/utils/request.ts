import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

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

// 刷新回调类型
type RefreshCallback = (token: string) => void

// 是否正在刷新 Token
let isRefreshing = false
// 是否正在处理登录过期（防止重复提示）
let isHandlingExpired = false
// 等待刷新的请求队列
let refreshSubscribers: RefreshCallback[] = []
// 刷新超时时间
const REFRESH_TIMEOUT = 30000

// 将请求加入队列（带超时处理）
const subscribeTokenRefresh = (cb: RefreshCallback, timeout = REFRESH_TIMEOUT): Promise<void> => {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      // 超时后从队列中移除（如果还在队列中）
      const index = refreshSubscribers.indexOf(wrappedCb)
      if (index > -1) {
        refreshSubscribers.splice(index, 1)
      }
      reject(new Error('Token refresh timeout'))
    }, timeout)

    const wrappedCb: RefreshCallback = (token: string) => {
      clearTimeout(timer)
      cb(token)
      resolve()
    }

    refreshSubscribers.push(wrappedCb)
  })
}

// 刷新完成后执行队列中的请求
const onRefreshed = (token: string) => {
  const subscribers = [...refreshSubscribers]
  refreshSubscribers = []
  subscribers.forEach((cb) => {
    try {
      cb(token)
    } catch (e) {
      console.error('Error in refresh subscriber:', e)
    }
  })
}

// 刷新失败，清空队列并跳转登录
const onRefreshFailed = (errorMessage?: string) => {
  // 先清空队列，防止后续请求继续等待
  const subscribers = [...refreshSubscribers]
  refreshSubscribers = []
  // 通知所有等待的请求失败
  subscribers.forEach((cb) => {
    try {
      // 传入空字符串表示失败
      cb('')
    } catch (e) {
      console.error('Error notifying refresh failed:', e)
    }
  })

  // 防止重复提示和跳转
  if (isHandlingExpired) {
    return
  }
  isHandlingExpired = true

  // 显示错误提示
  ElMessage.error(errorMessage || '登录已过期，请重新登录')

  // 执行登出和跳转
  const userStore = useUserStore()
  userStore.logoutAction().finally(() => {
    isHandlingExpired = false
    router.push('/login')
  })
}

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
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

    // code 不是 200 则为错误
    if (res.code !== 200) {
      // 401: 未登录或 Token 过期
      if (res.code === 401) {
        return handleTokenExpired(null, res.message)
      } else {
        ElMessage.error(res.message || '请求失败')
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    // 统一将分页结果中的 total 转为数字类型
    if (res.data && res.data.total !== undefined) {
      res.data.total = Number(res.data.total)
    }

    return res as any
  },
  (error) => {
    // 401 错误特殊处理 - 尝试刷新 Token
    if (error.response?.status === 401) {
      return handleTokenExpired(error)
    }

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
    return Promise.reject(error)
  }
)

// 处理 Token 过期
const handleTokenExpired = (error: any, errorMessage?: string) => {
  const userStore = useUserStore()
  const refreshTokenValue = userStore.refreshTokenValue

  // 如果没有 refreshToken，直接跳转登录
  if (!refreshTokenValue) {
    // 防止重复提示
    if (!isHandlingExpired) {
      isHandlingExpired = true
      ElMessage.error(errorMessage || '未登录或登录已过期')
      userStore.logoutAction().finally(() => {
        isHandlingExpired = false
        router.push('/login')
      })
    }
    return Promise.reject(error || new Error('No refresh token'))
  }

  // 如果正在刷新，将请求加入队列等待
  if (isRefreshing) {
    return subscribeTokenRefresh((token: string) => {
      // token 为空表示刷新失败
      if (!token) {
        return Promise.reject(error || new Error('Token refresh failed'))
      }
      // 使用新 Token 重新发起原请求
      const config = error?.config
      if (config) {
        config.headers.Authorization = `Bearer ${token}`
        return service.request(config)
      }
      return Promise.reject(error)
    }).then((result) => {
      // subscribeTokenRefresh 的回调返回了 Promise，这里需要处理
      return result
    }).catch(() => {
      return Promise.reject(error || new Error('Token refresh timeout'))
    })
  }

  // 开始刷新 Token
  isRefreshing = true

  return new Promise((resolve, reject) => {
    // 调用刷新 Token 接口，添加超时控制
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), REFRESH_TIMEOUT)

    axios
      .post<Result<any>>('/api/auth/refresh', { refreshToken: refreshTokenValue }, {
        signal: controller.signal
      })
      .then((response) => {
        clearTimeout(timeoutId)
        const res = response.data
        if (res.code === 200 && res.data) {
          const newToken = res.data.accessToken
          const newRefreshToken = res.data.refreshToken

          // 更新 Token
          userStore.updateToken(newToken)
          userStore.updateRefreshToken(newRefreshToken)

          // 执行等待队列中的请求
          onRefreshed(newToken)

          // 重新发起原请求
          if (error?.config) {
            error.config.headers.Authorization = `Bearer ${newToken}`
            resolve(service.request(error.config))
          } else {
            resolve(res.data)
          }
        } else {
          // 刷新失败
          onRefreshFailed(res.message || 'Token 刷新失败')
          reject(error || new Error('Token refresh failed'))
        }
      })
      .catch((err) => {
        clearTimeout(timeoutId)
        // 刷新失败
        const message = err.name === 'AbortError' || err.name === 'CanceledError'
          ? 'Token 刷新超时'
          : '登录已过期，请重新登录'
        onRefreshFailed(message)
        reject(err)
      })
      .finally(() => {
        isRefreshing = false
      })
  })
}

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

export default service

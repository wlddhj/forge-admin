import { API_BASE_URL } from '@/config'

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export async function request<T>(options: {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
}): Promise<T> {
  const header: Record<string, string> = {
    ...options.header
  }

  // 从 storage 获取 token
  const accessToken = uni.getStorageSync('accessToken')
  if (accessToken) {
    header['Authorization'] = 'Bearer ' + accessToken
  }

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: API_BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => {
        if (res.statusCode === 401) {
          // Token 过期，清除并跳转登录
          uni.removeStorageSync('accessToken')
          uni.removeStorageSync('refreshToken')
          uni.removeStorageSync('userInfo')
          uni.reLaunch({ url: '/pages/login/index' })
          reject(new Error('登录已过期'))
          return
        }
        const body = res.data as ApiResult<T>
        if (body.code === 200) {
          resolve(body.data)
        } else {
          uni.showToast({ title: body.message || '请求失败', icon: 'none' })
          reject(new Error(body.message))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(err)
      }
    })
  })
}
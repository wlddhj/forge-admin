import axios, { AxiosResponse, InternalAxiosRequestConfig, AxiosError } from 'axios'

const axiosInstance = axios.create({
  // forge-admin API baseURL（开发: localhost:8181/admin-api，生产: /admin-api）
  baseURL: import.meta.env.VITE_API_BASE || '/admin-api',
  timeout: 15000,
})

// 请求拦截器：从 URL query 读 token（iframe 模式 /#/chart?id=5&token=xxx）
axiosInstance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    try {
      const hash = window.location.hash
      const queryStr = hash.split('?')[1] || ''
      const params = new URLSearchParams(queryStr)
      const token = params.get('token')
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`
      }
    } catch { /* URL 解析失败，跳过头 */ }
    return config
  },
  (error: AxiosError) => Promise.reject(error)
)

// 响应拦截器：适配 forge-admin Result 格式 { code: 200, data: ... }
axiosInstance.interceptors.response.use(
  (res: AxiosResponse) => {
    const data = res.data
    // forge-admin 格式：{ code: 200, message: 'success', data: ... }
    if (data && data.code === 200) {
      return data.data  // 自动解包，goView 组件拿到的是 data.data
    }
    // 非 200 的响应
    if (data && data.code) {
      console.error('[goview api] error', data.code, data.message)
      return Promise.reject(new Error(data.message || `API error: ${data.code}`))
    }
    return data
  },
  (err: AxiosError) => {
    console.error('[goview api] request error', err)
    return Promise.reject(err)
  }
)

export default axiosInstance

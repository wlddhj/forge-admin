/**
 * 小程序全局配置
 */

/** 后端 App 端 API 基址，通过 VITE_API_BASE_URL 环境变量注入 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8181/app-api'

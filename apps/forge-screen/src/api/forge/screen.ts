/**
 * forge-admin 大屏 API 封装。
 * goView 通过这个模块调 forge-admin 后端的 /screen/* 接口。
 */
import request from '@/api/axios'

export interface ScreenDetail {
  id: number
  code: string
  name: string
  description?: string
  config: string
  configDraft: string
  theme: string
  status: number
  version: number
}

export interface ScreenUpdateRequest {
  id: number
  code?: string
  name?: string
  theme?: string
  config?: string
  description?: string
}

export const getScreenDetail = (id: number): Promise<ScreenDetail> =>
  request.get(`/screen/${id}`)

export const getScreenByCode = (code: string): Promise<ScreenDetail> =>
  request.get(`/screen/code/${code}`)

export const updateScreen = (data: ScreenUpdateRequest): Promise<void> =>
  request.put('/screen', data)

export const publishScreen = (code: string): Promise<void> =>
  request.put(`/screen/publish/${code}`)

export const deleteScreen = (ids: number[]): Promise<void> =>
  request.delete('/screen', { data: ids })

export const copyScreen = (
  code: string,
  data: { newCode: string; newName: string }
): Promise<number> =>
  request.post(`/screen/copy/${code}`, data)

/** 从 URL hash 取当前大屏 id（支持路径参数 /chart/home/:id 和 query 参数 ?id=xx） */
export function getScreenIdFromUrl(): number | null {
  try {
    const hash = window.location.hash
    // 先尝试 query 参数
    const queryStr = hash.split('?')[1] || ''
    const params = new URLSearchParams(queryStr)
    const queryId = params.get('id')
    if (queryId) return Number(queryId)
    // 再尝试路径参数 /chart/home/8
    const match = hash.match(/\/chart\/home\/(\d+)/)
    if (match) return Number(match[1])
    return null
  } catch {
    return null
  }
}

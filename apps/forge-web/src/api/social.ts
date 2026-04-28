import request from '@/utils/request'
import type { SocialBinding } from '@/types/social'

export const socialApi = {
  /** 绑定社交账号 */
  bind: (tempToken: string) => request.post('/auth/social/bind', { tempToken }),

  /** 解绑社交账号 */
  unbind: (source: string) => request.post('/auth/social/unbind', null, { params: { source } }),

  /** 获取已绑定的社交账号列表 */
  listBindings: () => request.get<SocialBinding[]>('/auth/social/bindings'),
}

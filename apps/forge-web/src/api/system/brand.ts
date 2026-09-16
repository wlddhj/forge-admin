import request from '@/utils/request'

export interface BrandConfig {
  /** 品牌 Logo URL，空串表示使用系统默认 Logo */
  logo: string
  /** 项目名称 */
  name: string
  /** 登录页主标题 */
  loginTitle: string
  /** 登录页副标题 */
  loginSubtitle: string
}

export const brandApi = {
  /** 公开获取品牌配置（免登录，登录页/布局启动时用，失败静默） */
  getPublicBrand: () =>
    request.get<BrandConfig>('/system/brand/public', { silent: true }).then(res => res.data),
  /** 获取品牌配置（需 system:brand:query 权限） */
  getBrand: () => request.get<BrandConfig>('/system/brand').then(res => res.data),
  /** 保存品牌配置（需 system:brand:update 权限） */
  updateBrand: (data: BrandConfig) => request.put('/system/brand', data)
}

import request from '@/utils/request'
import type { LayoutKind, Palette, StyleKind, ThemeMode } from '@/themes'

/** 系统默认主题（新用户首次进入时的初始主题，palette 不含 custom） */
export interface BrandThemeConfig {
  palette: Palette
  layout: LayoutKind
  style: StyleKind
  mode: ThemeMode
}

export interface BrandConfig {
  /** 品牌 Logo URL，空串表示使用系统默认 Logo */
  logo: string
  /** 项目名称 */
  name: string
  /** 登录页主标题 */
  loginTitle: string
  /** 登录页副标题 */
  loginSubtitle: string
  /** 系统默认主题（字段可能缺失，前端逐项回退默认） */
  defaultTheme?: Partial<BrandThemeConfig>
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

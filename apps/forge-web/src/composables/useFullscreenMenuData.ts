import { computed, ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import type { MenuTree } from '@/types/system'

export interface FullscreenMenuItem {
  id: number
  title: string
  icon: string
  fullPath: string
  isExternal: boolean
}

export interface FullscreenMenuGroup {
  id: number
  title: string
  icon: string
  items: FullscreenMenuItem[]
}

export interface FullscreenMenuCategory {
  id: number
  title: string
  icon: string
  count: number
  groups: FullscreenMenuGroup[]
}

/** 可导航节点：非按钮且未隐藏 */
const isNavNode = (m: MenuTree) => m.menuType !== 2 && m.visible !== 0

/** 路径拼接：外链/绝对路径短路，相对路径逐级累计（与 SidebarMenuItem 一致） */
export function joinPath(parent: string, p: string): string {
  if (!p) return parent
  if (/^(https?:)?\/\//i.test(p) || p.startsWith('/')) return p
  return parent ? `${parent}/${p}` : p
}

const toItem = (menu: MenuTree, fullPath: string): FullscreenMenuItem => ({
  id: menu.id,
  title: menu.menuName,
  icon: menu.icon || '',
  fullPath,
  isExternal: menu.isExternal === 1
})

/**
 * 二级节点 → 分组：存在可见非按钮子节点时三级为链接项，否则自身为单项组；
 * 无 routePath 的叶子不产生导航项
 */
function buildGroup(menu: MenuTree, parentPath: string): FullscreenMenuGroup | null {
  const navChildren = (menu.children || []).filter(isNavNode)
  if (navChildren.length > 0) {
    const secPath = joinPath(parentPath, menu.routePath)
    const items: FullscreenMenuItem[] = []
    for (const t of navChildren) {
      if (!t.routePath) continue
      items.push(toItem(t, joinPath(secPath, t.routePath)))
    }
    if (!items.length) return null
    return { id: menu.id, title: menu.menuName, icon: menu.icon || '', items }
  }
  if (!menu.routePath) return null
  const fullPath = joinPath(parentPath, menu.routePath)
  if (!fullPath) return null
  return { id: menu.id, title: menu.menuName, icon: menu.icon || '', items: [toItem(menu, fullPath)] }
}

/** 菜单树 → 分类结构（一级=分类；二级=分组；三级=条目；一级自身为叶子时退化为单项组） */
export function buildCategories(menus: MenuTree[]): FullscreenMenuCategory[] {
  const result: FullscreenMenuCategory[] = []
  for (const top of menus.filter(isNavNode)) {
    const navChildren = (top.children || []).filter(isNavNode)
    const groups: FullscreenMenuGroup[] = []
    if (navChildren.length > 0) {
      for (const sec of navChildren) {
        const g = buildGroup(sec, top.routePath)
        if (g) groups.push(g)
      }
    } else {
      const g = buildGroup(top, '')
      if (g) groups.push(g)
    }
    if (groups.length > 0) {
      const count = groups.reduce((n, g) => n + g.items.length, 0)
      result.push({ id: top.id, title: top.menuName, icon: top.icon || '', count, groups })
    }
  }
  return result
}

/** 回退源（permissionStore.routes 平铺路由）→ 单一虚拟分类 */
function buildFromRoutes(children: RouteRecordRaw[]): FullscreenMenuCategory[] {
  const items = children
    .filter(r => !r.meta?.hidden && r.path)
    .map((r, idx) => ({
      id: (r.meta?.menuId as number) ?? -(idx + 1),
      title: (r.meta?.title as string) || r.path,
      icon: (r.meta?.icon as string) || '',
      fullPath: r.path,
      isExternal: false
    }))
  if (!items.length) return []
  return [{
    id: -1,
    title: '功能菜单',
    icon: 'Menu',
    count: items.length,
    groups: items.map(it => ({ id: it.id, title: it.title, icon: it.icon, items: [it] }))
  }]
}

/** 搜索过滤：一级命中保留整分类；二级命中保留整组；三级命中保留命中项 */
export function filterByKeyword(
  categories: FullscreenMenuCategory[],
  keyword: string
): FullscreenMenuCategory[] {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return categories
  return categories
    .map(cat => {
      if (cat.title.toLowerCase().includes(kw)) return cat
      const groups = cat.groups
        .map(g => {
          if (g.title.toLowerCase().includes(kw)) return g
          const items = g.items.filter(it => it.title.toLowerCase().includes(kw))
          return items.length ? { ...g, items } : null
        })
        .filter((g): g is FullscreenMenuGroup => g !== null)
      // 过滤后重算 count，保证徽标与「N 项」标题反映实际展示数量
      return groups.length
        ? { ...cat, groups, count: groups.reduce((n, g) => n + g.items.length, 0) }
        : null
    })
    .filter((c): c is FullscreenMenuCategory => c !== null)
}

export function useFullscreenMenuData() {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  const keyword = ref('')

  const categories = computed<FullscreenMenuCategory[]>(() => {
    const menus = userStore.menus
    if (menus && menus.length > 0) return buildCategories(menus)
    const children = permissionStore.routes.find(r => r.path === '/')?.children || []
    return buildFromRoutes(children)
  })

  const filteredCategories = computed(() => filterByKeyword(categories.value, keyword.value))

  return { keyword, categories, filteredCategories }
}

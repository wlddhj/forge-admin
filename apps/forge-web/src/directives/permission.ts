import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 权限指令
 * 使用方式: v-permission="'system:user:add'" 或 v-permission="['system:user:add', 'system:user:edit']"
 */
export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const userStore = useUserStore()
    const permissions = userStore.userInfo?.permissions || []

    const value = binding.value
    if (!value) return

    const requiredPermissions = Array.isArray(value) ? value : [value]

    // 检查是否有任一权限
    const hasPermission = requiredPermissions.some(p => permissions.includes(p))

    if (!hasPermission) {
      el.parentNode?.removeChild(el)
    }
  }
}

/**
 * 角色指令
 * 使用方式: v-role="'admin'" 或 v-role="['admin', 'editor']"
 */
export const role: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const userStore = useUserStore()
    const roles = userStore.userInfo?.roles || []

    const value = binding.value
    if (!value) return

    const requiredRoles = Array.isArray(value) ? value : [value]

    // 检查是否有任一角色
    const hasRole = requiredRoles.some(r => roles.includes(r))

    if (!hasRole) {
      el.parentNode?.removeChild(el)
    }
  }
}

/**
 * 权限判断函数
 * @param permission 权限标识
 * @returns 是否有权限
 */
export function hasPermission(permission: string | string[]): boolean {
  const userStore = useUserStore()
  const permissions = userStore.userInfo?.permissions || []

  const requiredPermissions = Array.isArray(permission) ? permission : [permission]
  return requiredPermissions.some(p => permissions.includes(p))
}

/**
 * 角色判断函数
 * @param role 角色标识
 * @returns 是否有角色
 */
export function hasRole(role: string | string[]): boolean {
  const userStore = useUserStore()
  const roles = userStore.userInfo?.roles || []

  const requiredRoles = Array.isArray(role) ? role : [role]
  return requiredRoles.some(r => roles.includes(r))
}

/**
 * 检查是否有任一权限
 */
export function hasAnyPermission(permissions: string[]): boolean {
  return hasPermission(permissions)
}

/**
 * 检查是否有所有权限
 */
export function hasAllPermissions(permissions: string[]): boolean {
  const userStore = useUserStore()
  const userPermissions = userStore.userInfo?.permissions || []
  return permissions.every(p => userPermissions.includes(p))
}

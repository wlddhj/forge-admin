import type { ScreenCardComponent } from './types'

export interface Registry<T> {
  register(entry: T): void
  get(type: string): T | undefined
  list(): T[]
  has(type: string): boolean
}

export function createRegistry<T extends { type: string }>(): Registry<T> {
  const map = new Map<string, T>()
  return {
    register(entry) {
      if (map.has(entry.type)) throw new Error(`Card type already registered: ${entry.type}`)
      map.set(entry.type, entry)
    },
    get(type) { return map.get(type) },
    list() { return Array.from(map.values()) },
    has(type) { return map.has(type) }
  }
}

/** 全局卡片注册中心（模块单例） */
export const cardRegistry: Registry<ScreenCardComponent> = createRegistry<ScreenCardComponent>()

/**
 * 注册内置 8 个卡片。
 * Task 5/6 完成对应组件后，此处替换为真实 import + register。
 */
export function registerBuiltinCards(): void {
  // Task 5/6 会把 import 写到这里。
}

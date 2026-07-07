import { ref, computed, toRaw, type Ref, type ComputedRef } from 'vue'

export interface ScreenHistoryReturn<T> {
  state: Ref<T>
  canUndo: ComputedRef<boolean>
  canRedo: ComputedRef<boolean>
  commit: (next: T) => void
  undo: () => void
  redo: () => void
  clear: () => void
}

function clone<T>(v: T): T {
  // toRaw 解包 reactive/ref proxy，再 JSON 兜底（处理 Date/RegExp 等）
  return JSON.parse(JSON.stringify(toRaw(v)))
}

export function useScreenHistory<T>(initial: T, options: { max?: number } = {}): ScreenHistoryReturn<T> {
  const max = options.max ?? 50
  const undoStack = ref<T[]>([])
  const redoStack = ref<T[]>([])
  const state = ref(clone(initial)) as Ref<T>

  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  const commit = (next: T) => {
    undoStack.value.push(clone(state.value))
    if (undoStack.value.length > max) undoStack.value.shift()
    state.value = clone(next)
    redoStack.value = []
  }

  const undo = () => {
    if (!canUndo.value) return
    redoStack.value.push(clone(state.value))
    state.value = undoStack.value.pop()!
  }

  const redo = () => {
    if (!canRedo.value) return
    undoStack.value.push(clone(state.value))
    state.value = redoStack.value.pop()!
  }

  const clear = () => { undoStack.value = []; redoStack.value = [] }

  return { state, canUndo, canRedo, commit, undo, redo, clear }
}
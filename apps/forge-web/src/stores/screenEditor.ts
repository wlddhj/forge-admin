import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { nanoid } from 'nanoid'
import type { ScreenConfig, ScreenCard } from '@/types/screen'

const MAX_HISTORY = 50

export const useScreenEditorStore = defineStore('screenEditor', () => {
  const config = ref<ScreenConfig>({ version: 1, theme: 'dark-tech', cards: [] })
  const activeCardId = ref<string | null>(null)
  const undoStack = ref<ScreenConfig[]>([])
  const redoStack = ref<ScreenConfig[]>([])
  const isDirty = ref(false)
  const screenId = ref<number | null>(null)
  const screenCode = ref<string>('')

  const activeCard = computed<ScreenCard | null>(() =>
    config.value.cards.find(c => c.id === activeCardId.value) ?? null
  )

  const snapshot = (): ScreenConfig => JSON.parse(JSON.stringify(config.value))

  const applyChange = (mutator: (draft: ScreenConfig) => void) => {
    undoStack.value.push(snapshot())
    if (undoStack.value.length > MAX_HISTORY) undoStack.value.shift()
    const next = snapshot()
    mutator(next)
    config.value = next
    redoStack.value = []
    isDirty.value = true
  }

  const undo = () => {
    if (undoStack.value.length === 0) return
    redoStack.value.push(snapshot())
    config.value = undoStack.value.pop()!
    isDirty.value = true
  }

  const redo = () => {
    if (redoStack.value.length === 0) return
    undoStack.value.push(snapshot())
    config.value = redoStack.value.pop()!
    isDirty.value = true
  }

  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  const addCard = (type: string, position: { x: number; y: number }) => {
    const newCard: ScreenCard = {
      id: nanoid(8),
      type, x: position.x, y: position.y, w: 6, h: 4,
      dataSourceId: null, refresh: 0, options: {}
    }
    applyChange(d => { d.cards.push(newCard) })
    activeCardId.value = newCard.id
  }

  const removeCard = (id: string) => {
    applyChange(d => { d.cards = d.cards.filter(c => c.id !== id) })
    if (activeCardId.value === id) activeCardId.value = null
  }

  const updateCard = (id: string, patch: Partial<ScreenCard>) => {
    applyChange(d => {
      const c = d.cards.find(x => x.id === id)
      if (c) Object.assign(c, patch)
    })
  }

  const markClean = () => { isDirty.value = false }

  const reset = () => {
    config.value = { version: 1, theme: 'dark-tech', cards: [] }
    activeCardId.value = null
    undoStack.value = []
    redoStack.value = []
    isDirty.value = false
  }

  return {
    config, activeCardId, activeCard, isDirty, screenId, screenCode,
    canUndo, canRedo,
    applyChange, undo, redo,
    addCard, removeCard, updateCard,
    markClean, reset
  }
})
